package com.aurora.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.aurora.app.util.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Owns the whole BLE lifecycle: scan → connect → MTU → discovery → CCCD →
 * serialized writes with FIFO response awaiters (firmware answers 1:1, in order).
 *
 * All public state is StateFlow (thread-safe); GATT callbacks arrive on binder
 * threads. One GATT operation is in flight at a time — Android silently fails
 * otherwise — so descriptor setup must finish before the command pump starts.
 */
@SuppressLint("MissingPermission") // permission gate lives in the UI before any call
class BleManager(context: Context) {

    private val appContext = context.applicationContext

    sealed class ConnectionState {
        data object Idle : ConnectionState()
        data object Scanning : ConnectionState()
        data class Connecting(val address: String) : ConnectionState()
        data class Connected(val name: String, val address: String) : ConnectionState()
        data class Disconnected(val reason: String) : ConnectionState()
    }

    data class FoundDevice(val name: String, val address: String, val rssi: Int)

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    /** Every response line received, for opportunistic listeners (mode sync etc.). */
    private val _responses = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val responses: SharedFlow<String> = _responses.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lock = Any()

    private var adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private var cccdWriteInFlight = false
    private var ready = false
    private var scanTimeoutJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var rssiJob: Job? = null
    private var autoReconnect = true
    private var retryCount = 0
    private var currentDeviceName: String = ""
    private var currentAddress: String = ""

    private val awaiters = ArrayDeque<CompletableDeferred<String>>()
    private val lineBuffer = BleProtocol.LineBuffer()

    private val prefs = appContext.getSharedPreferences("aurora", Context.MODE_PRIVATE)
    fun lastDeviceAddress(): String? = prefs.getString("last_device", null)

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    // ---------------------------------------------------------------- scanning

    fun startScan() {
        stopScanInternal()
        _devices.value = emptyList()
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = ConnectionState.Disconnected("Bluetooth off")
            return
        }
        _state.value = ConnectionState.Scanning
        AppLog.add(AppLog.Dir.SYSTEM, "Scanning for '${BleProtocol.DEVICE_NAME_FILTER}' …")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(emptyList(), settings, scanCallback)
        } catch (e: SecurityException) {
            _state.value = ConnectionState.Disconnected("Bluetooth permission missing")
            AppLog.add(AppLog.Dir.SYSTEM, "Scan blocked: ${e.message}")
            return
        }
        scanTimeoutJob = scope.launch {
            delay(SCAN_MS)
            stopScanInternal()
            if (_state.value is ConnectionState.Scanning) _state.value = ConnectionState.Idle
            AppLog.add(AppLog.Dir.SYSTEM, "Scan finished — ${_devices.value.size} device(s)")
        }
    }

    fun stopScan() {
        stopScanInternal()
        if (_state.value is ConnectionState.Scanning) _state.value = ConnectionState.Idle
    }

    private fun stopScanInternal() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (!BleProtocol.matchesDeviceName(name)) return
            val found = FoundDevice(name, result.device.address, result.rssi)
            val current = _devices.value
            val idx = current.indexOfFirst { it.address == found.address }
            val next = if (idx >= 0) {
                current.toMutableList().also { it[idx] = found }
            } else {
                current + found
            }
            _devices.value = next.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            AppLog.add(AppLog.Dir.SYSTEM, "Scan failed: $errorCode")
            _state.value = ConnectionState.Disconnected("Scan failed ($errorCode)")
        }
    }

    // --------------------------------------------------------------- connecting

    fun connect(address: String, name: String) {
        stopScanInternal()
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (device == null) {
            _state.value = ConnectionState.Disconnected("Bad address $address")
            return
        }
        autoReconnect = true
        retryCount = 0
        prefs.edit().putString("last_device", address).apply()
        initiate(device.address, name)
    }

    private fun initiate(address: String, name: String) {
        releaseGatt()
        currentAddress = address
        currentDeviceName = name
        _state.value = ConnectionState.Connecting(address)
        AppLog.add(AppLog.Dir.SYSTEM, "Connecting to $name ($address) …")
        val device = adapter?.getRemoteDevice(address) ?: run {
            _state.value = ConnectionState.Disconnected("Adapter unavailable")
            return
        }
        gatt = try {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _state.value = ConnectionState.Disconnected("Bluetooth permission missing")
            AppLog.add(AppLog.Dir.SYSTEM, "connectGatt blocked: ${e.message}")
            return
        }
        connectTimeoutJob = scope.launch {
            delay(CONNECT_MS)
            if (_state.value is ConnectionState.Connecting) {
                AppLog.add(AppLog.Dir.SYSTEM, "Connect timeout")
                fail("Connect timeout", retry = true)
            }
        }
    }

    fun disconnectByUser() {
        autoReconnect = false
        connectTimeoutJob?.cancel()
        rssiJob?.cancel()
        _state.value = ConnectionState.Disconnected("Disconnected by user")
        AppLog.add(AppLog.Dir.SYSTEM, "Disconnect requested")
        releaseGatt()
        failAllAwaiters("Disconnected")
    }

    fun close() {
        autoReconnect = false
        stopScanInternal()
        connectTimeoutJob?.cancel()
        rssiJob?.cancel()
        releaseGatt()
        failAllAwaiters("Closed")
        scope.coroutineContext[Job]?.cancel()
    }

    private fun releaseGatt() {
        ready = false
        synchronized(lock) {
            writeQueue.clear()
            writeInFlight = false
            cccdWriteInFlight = false
        }
        val g = gatt
        gatt = null
        writeChar = null
        notifyChar = null
        lineBuffer.reset()
        _rssi.value = null // don't show a stale dBm from the previous session
        if (g != null) {
            try {
                g.disconnect()
            } catch (_: Exception) {
            }
            try {
                g.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun fail(reason: String, retry: Boolean) {
        // User-initiated disconnect already set a terminal state — don't overwrite it
        // when the delayed STATE_DISCONNECTED callback arrives.
        if (!autoReconnect && _state.value is ConnectionState.Disconnected) return
        connectTimeoutJob?.cancel()
        releaseGatt()
        failAllAwaiters(reason)
        if (retry && autoReconnect && retryCount < MAX_RETRIES) {
            retryCount++
            AppLog.add(AppLog.Dir.SYSTEM, "Reconnect attempt $retryCount/$MAX_RETRIES in 2 s")
            scope.launch {
                delay(2000)
                if (autoReconnect) initiate(currentAddress, currentDeviceName)
            }
        } else {
            _state.value = ConnectionState.Disconnected(reason)
        }
    }

    // -------------------------------------------------------------------- gatt

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                AppLog.add(AppLog.Dir.SYSTEM, "GATT status $status")
                fail("Connection lost ($status)", retry = true)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    AppLog.add(AppLog.Dir.SYSTEM, "Connected — requesting MTU 517")
                    val requested = try {
                        g.requestMtu(517)
                    } catch (_: SecurityException) {
                        false
                    }
                    if (!requested) try {
                        g.discoverServices()
                    } catch (_: SecurityException) {
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    AppLog.add(AppLog.Dir.SYSTEM, "Disconnected from device")
                    fail("Connection lost", retry = true)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            AppLog.add(AppLog.Dir.SYSTEM, "MTU = $mtu (${if (status == 0) "ok" else "status $status"})")
            try {
                g.discoverServices()
            } catch (_: SecurityException) {
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Service discovery failed", retry = false)
                return
            }
            val service = g.getService(UUID.fromString(BleProtocol.SERVICE_UUID))
            val w = service?.getCharacteristic(UUID.fromString(BleProtocol.CHAR_WRITE_UUID))
            val n = service?.getCharacteristic(UUID.fromString(BleProtocol.CHAR_NOTIFY_UUID))
            if (w == null || n == null) {
                AppLog.add(AppLog.Dir.SYSTEM, "Aurora GATT service/characteristics missing")
                fail("Not an Aurora device", retry = false)
                return
            }
            writeChar = w
            notifyChar = n
            AppLog.add(AppLog.Dir.SYSTEM, "Services found — enabling notifications")
            enableNotifications(g, n)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid.toString().equals(BleProtocol.CCCD_UUID, ignoreCase = true)) {
                synchronized(lock) { cccdWriteInFlight = false }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Notify enable failed", retry = false)
                    return
                }
                ready = true
                retryCount = 0
                _state.value = ConnectionState.Connected(currentDeviceName, currentAddress)
                AppLog.add(AppLog.Dir.SYSTEM, "Ready — connected to $currentDeviceName")
                startRssiPoll()
                pump()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val next: ByteArray?
            synchronized(lock) {
                writeInFlight = false
                next = writeQueue.removeFirstOrNull()
                if (next != null) writeInFlight = true
            }
            if (next != null) {
                writeBytes(next)
            } else {
                pump()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onNotifyBytes(value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            onNotifyBytes(value)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _rssi.value = rssi
        }
    }

    private fun enableNotifications(g: BluetoothGatt, n: BluetoothGattCharacteristic) {
        val ok = try {
            g.setCharacteristicNotification(n, true)
        } catch (_: SecurityException) {
            false
        }
        if (!ok) {
            fail("Notify setup failed", retry = false)
            return
        }
        @Suppress("DEPRECATION")
        val cccd = n.getDescriptor(UUID.fromString(BleProtocol.CCCD_UUID))
        if (cccd == null) {
            fail("CCCD missing", retry = false)
            return
        }
        @Suppress("DEPRECATION")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        synchronized(lock) { cccdWriteInFlight = true }
        val started = try {
            g.writeDescriptor(cccd)
        } catch (_: SecurityException) {
            false
        }
        if (!started) {
            synchronized(lock) { cccdWriteInFlight = false }
            fail("CCCD write rejected", retry = false)
        }
    }

    private fun onNotifyBytes(value: ByteArray) {
        val lines = lineBuffer.feed(String(value, Charsets.UTF_8))
        for (line in lines) {
            AppLog.add(AppLog.Dir.RECV, line)
            val waiter: CompletableDeferred<String>?
            synchronized(lock) { waiter = awaiters.removeFirstOrNull() }
            waiter?.complete(line)
            _responses.tryEmit(line)
        }
    }

    // ------------------------------------------------------------ write pump

    private fun enqueue(bytes: ByteArray) {
        synchronized(lock) {
            writeQueue.addLast(bytes)
        }
        pump()
    }

    private fun pump() {
        if (!ready) return
        val next: ByteArray?
        synchronized(lock) {
            if (writeInFlight || cccdWriteInFlight) return
            next = writeQueue.removeFirstOrNull()
            if (next != null) writeInFlight = true
        }
        next?.let { writeBytes(it) }
    }

    @Suppress("DEPRECATION")
    private fun writeBytes(bytes: ByteArray) {
        val g = gatt
        val w = writeChar
        if (g == null || w == null) {
            synchronized(lock) { writeInFlight = false }
            return
        }
        w.value = bytes
        w.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted = try {
            g.writeCharacteristic(w)
        } catch (_: SecurityException) {
            false
        }
        if (!accepted) {
            AppLog.add(AppLog.Dir.SYSTEM, "Write rejected — dropping command")
            synchronized(lock) { writeInFlight = false }
            pump()
        }
    }

    /**
     * Send [command] and await its single response line.
     * Returns null on timeout / not-connected. Callers must be on a coroutine.
     */
    suspend fun request(command: String, timeoutMs: Long = 2500): String? {
        if (!ready) {
            AppLog.add(AppLog.Dir.SENT, "$command (dropped — not connected)")
            return null
        }
        AppLog.add(AppLog.Dir.SENT, command)
        val waiter = CompletableDeferred<String>()
        synchronized(lock) { awaiters.addLast(waiter) }
        enqueue(BleProtocol.encode(command))
        val result = withTimeoutOrNull(timeoutMs) { waiter.await() }
        if (result == null) {
            synchronized(lock) { awaiters.remove(waiter) }
            AppLog.add(AppLog.Dir.SYSTEM, "Timeout: $command")
            return null
        }
        // "" is the sentinel used when the connection dropped mid-request.
        if (result.isEmpty()) return null
        return result
    }

    /** Fire-and-forget: still registers an awaiter so responses stay in order. */
    fun send(command: String) {
        scope.launch { request(command) }
    }

    private fun failAllAwaiters(reason: String) {
        val pending: List<CompletableDeferred<String>>
        synchronized(lock) {
            pending = awaiters.toList()
            awaiters.clear()
        }
        // Complete (don't cancel) — a cancelled deferred would throw
        // CancellationException inside the caller's coroutine.
        pending.forEach { it.complete("") }
        if (pending.isNotEmpty()) AppLog.add(AppLog.Dir.SYSTEM, "Cancelled ${pending.size} pending request(s): $reason")
    }

    private fun startRssiPoll() {
        rssiJob?.cancel()
        rssiJob = scope.launch {
            while (isActive) {
                delay(5000)
                val idle = synchronized(lock) { !writeInFlight && !cccdWriteInFlight && writeQueue.isEmpty() }
                if (idle && ready) {
                    try {
                        gatt?.readRemoteRssi()
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
    }

    companion object {
        private const val SCAN_MS = 10_000L
        private const val CONNECT_MS = 30_000L
        private const val MAX_RETRIES = 5
    }
}
