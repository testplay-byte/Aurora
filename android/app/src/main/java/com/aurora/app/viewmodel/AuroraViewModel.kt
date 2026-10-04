package com.aurora.app.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.app.ble.BleManager
import com.aurora.app.ble.BleProtocol
import com.aurora.app.ble.GeneralSettings
import com.aurora.app.ble.ModeEditorState
import com.aurora.app.ble.Rgb
import com.aurora.app.model.Mode
import com.aurora.app.util.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * UI state glue: routes, current mode, settings cache, editor state.
 * BLE transport lives in [BleManager]; every command funnels through [request].
 */
class AuroraViewModel(application: Application) : AndroidViewModel(application) {

    val ble = BleManager(application)

    // ------------------------------------------------------------------ routes

    sealed interface Route {
        data object Scan : Route
        data class Main(val tab: Tab) : Route
        data class Editor(val mode: Int) : Route
    }

    enum class Tab { CONTROL, MODES, SETTINGS, LOG }

    private val _route = MutableStateFlow<Route>(Route.Scan)
    val route: StateFlow<Route> = _route.asStateFlow()

    fun openTab(tab: Tab) {
        if (_route.value is Route.Main || _route.value is Route.Scan) {
            _route.value = Route.Main(tab)
        }
    }

    fun backFromEditor() {
        editorLoadJob?.cancel()
        _route.value = Route.Main(Tab.MODES)
    }

    // ------------------------------------------------------- permissions (UI)

    /** Snapshot state so the Scan screen recomposes the moment the dialog resolves. */
    var hasPermission by mutableStateOf(checkBluetoothPermissions())
        private set

    fun refreshPermissionState() {
        hasPermission = checkBluetoothPermissions()
    }

    private fun checkBluetoothPermissions(): Boolean {
        val ctx = getApplication<Application>()
        return arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            .all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    }

    // ------------------------------------------------------------------ state

    /** -1 = unknown (before first STATUS). */
    private val _currentMode = MutableStateFlow(-1)
    val currentMode: StateFlow<Int> = _currentMode.asStateFlow()

    private val _general = MutableStateFlow(GeneralSettings())
    val general: StateFlow<GeneralSettings> = _general.asStateFlow()

    /** false = GETSETTINGS failed/truncated → Settings shows defaults + a notice. */
    private val _generalLoaded = MutableStateFlow(true)
    val generalLoaded: StateFlow<Boolean> = _generalLoaded.asStateFlow()

    /** JSON keys the user actually touched — only these are ever written back. */
    private val _generalDirty = MutableStateFlow<Set<String>>(emptySet())

    private val _editorState = MutableStateFlow<ModeEditorState?>(null)
    val editorState: StateFlow<ModeEditorState?> = _editorState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    private val _toast = Channel<String>(Channel.UNLIMITED)
    val toasts: Channel<String> = _toast

    private var editorPushJob: Job? = null
    private var solidPushJob: Job? = null
    private var editorLoadJob: Job? = null
    private var syncOnConnect = true

    init {
        // Connection lifecycle → initial sync + route switch from the scan screen.
        viewModelScope.launch {
            ble.state.collect { st ->
                when (st) {
                    is BleManager.ConnectionState.Connected -> {
                        if (syncOnConnect) {
                            syncOnConnect = false
                            initialSync()
                        }
                        if (_route.value is Route.Scan) {
                            _route.value = Route.Main(Tab.CONTROL)
                        }
                    }
                    is BleManager.ConnectionState.Disconnected -> {
                        syncOnConnect = true
                        _currentMode.value = -1
                        _latencyMs.value = null
                        if (st.reason == "Disconnected by user") {
                            _route.value = Route.Scan
                        } else if (_route.value is Route.Editor) {
                            // Don't strand the user editing a dead box.
                            editorLoadJob?.cancel()
                            _route.value = Route.Main(Tab.MODES)
                        }
                    }
                    else -> Unit
                }
            }
        }
        // Opportunistic sync: any MODE/STATUS that arrives updates the dashboard.
        viewModelScope.launch {
            ble.responses.collect { line -> handleUnsolicited(line) }
        }
    }

    // --------------------------------------------------------------- plumbing

    private fun toast(msg: String) {
        _toast.trySend(msg)
    }

    private suspend fun request(cmd: String): String? {
        val resp = ble.request(cmd)
        if (resp == null) toast("No response — device may be out of range")
        else handleResponse(resp)
        return resp
    }

    private fun isError(resp: String?): Boolean =
        resp == null || BleProtocol.parse(resp) is BleProtocol.Response.Error

    private fun handleResponse(line: String) {
        when (val r = BleProtocol.parse(line)) {
            is BleProtocol.Response.Error -> toast("Device error: ${r.code}")
            is BleProtocol.Response.Ok -> when {
                // `OK:MODE:<n>:<NAME>` confirms a mode switch.
                r.detail.startsWith("MODE:") -> {
                    val inner = BleProtocol.parse(r.detail)
                    if (inner is BleProtocol.Response.ModeValue) _currentMode.value = inner.mode
                }
                // `OK:OFF` confirms STOP — mode is now 0.
                r.detail == "OFF" -> _currentMode.value = Mode.OFF.id
                else -> Unit
            }
            else -> Unit
        }
    }

    private fun handleUnsolicited(line: String) {
        when (val r = BleProtocol.parse(line)) {
            is BleProtocol.Response.ModeValue -> _currentMode.value = r.mode
            else -> Unit
        }
    }

    private suspend fun initialSync() {
        request(BleProtocol.status())
        // GETSETTINGS may be truncated (full dump > one notify) — never treat a
        // failed load as data; flag it so Settings shows a notice instead.
        val resp = ble.request(BleProtocol.getSettings())
        val parsed = resp?.let { BleProtocol.parse(it) }
        if (parsed is BleProtocol.Response.Settings) {
            val root = runCatching { JSONObject(parsed.json) }.getOrNull()
            if (root != null) {
                _general.value = GeneralSettings.fromSettingsRoot(root)
                _generalDirty.value = emptySet()
                _generalLoaded.value = true
                return
            }
        }
        _generalLoaded.value = false
        AppLog.add(AppLog.Dir.SYSTEM, "GETSETTINGS unavailable/truncated — using defaults (safe mode)")
    }

    // ------------------------------------------------------------- scan / link

    fun startScan() {
        if (!ble.isBluetoothOn()) {
            toast("Turn on Bluetooth to scan")
            return
        }
        ble.startScan()
    }

    fun connectTo(device: BleManager.FoundDevice) {
        ble.stopScan()
        ble.connect(device.address, device.name)
    }

    fun disconnect() {
        ble.disconnectByUser()
        syncOnConnect = true
        _route.value = Route.Scan
    }

    // ------------------------------------------------------------ mode control

    fun switchMode(mode: Int) {
        viewModelScope.launch {
            _busy.value = true
            request(if (mode == Mode.OFF.id) BleProtocol.off() else BleProtocol.setMode(mode))
            _busy.value = false
        }
    }

    fun refreshMode() {
        viewModelScope.launch { request(BleProtocol.getMode()) }
    }

    fun ping() {
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val resp = ble.request(BleProtocol.ping())
            if (resp != null && BleProtocol.parse(resp) is BleProtocol.Response.Pong) {
                _latencyMs.value = System.currentTimeMillis() - t0
                toast("Pong · ${_latencyMs.value} ms")
            } else {
                toast("No pong")
            }
        }
    }

    // ----------------------------------------------------------------- editor

    fun openEditor(modeId: Int) {
        if (modeId == Mode.OFF.id) return
        editorLoadJob?.cancel()
        _route.value = Route.Editor(modeId)
        _editorState.value = null
        editorLoadJob = viewModelScope.launch {
            val resp = ble.request(BleProtocol.getModeSettings(modeId))
            val parsed = resp?.let { BleProtocol.parse(it) }
            // Route may have changed while we waited (back / disconnect).
            if ((_route.value as? Route.Editor)?.mode != modeId) return@launch
            _editorState.value = if (parsed is BleProtocol.Response.ModeSettings && parsed.mode == modeId) {
                ModeEditorState.fromJson(modeId, parsed.json)
            } else {
                toast("Couldn't load mode settings — using defaults")
                ModeEditorState.defaultFor(modeId)
            }
        }
    }

    /** Replace the editor state and stream it to the device (debounced). */
    fun patchEditor(next: ModeEditorState) {
        val modeId = (_route.value as? Route.Editor)?.mode ?: return
        _editorState.value = next
        editorPushJob?.cancel()
        editorPushJob = viewModelScope.launch {
            if (modeId == Mode.SOLID.id) return@launch // SOLID streams via LED: instead
            delay(250)
            ble.request(BleProtocol.setModeSettings(modeId, next.toJson().toString()))
                ?.let { handleResponse(it) }
        }
    }

    /** SOLID live preview: update corner + stream LED:<corner>:<r>,<g>,<b> (debounced). */
    fun solidCornerChanged(corner: Int, color: Rgb) {
        val cur = _editorState.value as? ModeEditorState.Solid ?: return
        val corners = cur.corners.toMutableList()
        corners[corner] = color.clamped()
        _editorState.value = cur.copy(corners = corners)
        solidPushJob?.cancel()
        solidPushJob = viewModelScope.launch {
            delay(100)
            ble.request(BleProtocol.led(corner, color.r, color.g, color.b))
                ?.let { handleResponse(it) }
        }
    }

    /** SOLID "same color everywhere": ALL:<r>,<g>,<b> + update local corners. */
    fun solidAllCorners(color: Rgb) {
        val cur = _editorState.value as? ModeEditorState.Solid ?: return
        _editorState.value = cur.copy(corners = List(4) { color.clamped() })
        viewModelScope.launch {
            ble.request(BleProtocol.all(color.r, color.g, color.b))?.let { handleResponse(it) }
        }
    }

    /**
     * Persist current edit to NVS. Honest toasts: success only when the device
     * actually acknowledged; SOLID corners are flushed (the debounce job is
     * cancelled, so the last edit would otherwise be lost).
     */
    fun saveEditor() {
        viewModelScope.launch {
            editorPushJob?.cancel()
            solidPushJob?.cancel()
            val modeId = (_route.value as? Route.Editor)?.mode
            val state = _editorState.value
            if (modeId == null || state == null) {
                toast("Nothing to save yet")
                return@launch
            }
            var ok = true
            if (state is ModeEditorState.Solid) {
                state.corners.forEachIndexed { i, c ->
                    if (isError(ble.request(BleProtocol.led(i, c.r, c.g, c.b)))) ok = false
                }
            } else {
                if (isError(ble.request(BleProtocol.setModeSettings(modeId, state.toJson().toString())))) ok = false
            }
            if (isError(ble.request(BleProtocol.save()))) ok = false
            if (ok) toast("Saved to device storage") // errors already toasted per-request
        }
    }

    /** Preview the edited mode (switch device to it). */
    fun runEditor() {
        val modeId = (_route.value as? Route.Editor)?.mode ?: return
        switchMode(modeId)
    }

    // ---------------------------------------------------------------- settings

    /**
     * Update display state and record WHICH json keys changed so saveGeneral
     * only writes fields the user touched (never clobbers unread device data).
     */
    fun updateGeneral(g: GeneralSettings, vararg changedKeys: String) {
        _general.value = g
        if (changedKeys.isNotEmpty()) {
            _generalDirty.value = _generalDirty.value + changedKeys.toSet()
        }
    }

    fun saveGeneral() {
        viewModelScope.launch {
            val g = _general.value
            if (!GeneralSettings.TIME_REGEX.matches(g.autoOnTime) || !GeneralSettings.TIME_REGEX.matches(g.autoOffTime)) {
                toast("Times must look like 18:00")
                return@launch
            }
            val dirty = _generalDirty.value
            if (dirty.isEmpty()) {
                toast("Nothing changed yet")
                return@launch
            }
            val resp = ble.request(BleProtocol.setSettings(g.toPatchFor(dirty).toString()))
            when {
                resp == null -> toast("Save failed — no response")
                BleProtocol.parse(resp) is BleProtocol.Response.Error ->
                    handleResponse(resp) // shows "Device error: …"
                else -> {
                    _generalDirty.value = emptySet()
                    toast("Saved ${dirty.size} setting(s)")
                }
            }
        }
    }

    fun clearLog() = AppLog.clear()

    override fun onCleared() {
        ble.close()
        super.onCleared()
    }
}
