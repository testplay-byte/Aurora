package com.aurora.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.app.ble.BleManager
import com.aurora.app.ui.screens.ControlScreen
import com.aurora.app.ui.screens.EditorScreen
import com.aurora.app.ui.screens.LogScreen
import com.aurora.app.ui.screens.ModesScreen
import com.aurora.app.ui.screens.ScanScreen
import com.aurora.app.ui.screens.SettingsScreen
import com.aurora.app.ui.theme.AuroraTheme
import com.aurora.app.viewmodel.AuroraViewModel
import com.aurora.app.viewmodel.AuroraViewModel.Tab
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private var pendingPermissionAction: (() -> Unit)? = null
    private var vmRef: AuroraViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Permission state is snapshot state on the VM — refresh so the
        // Scan screen flips out of the "Grant permission" card immediately.
        vmRef?.refreshPermissionState()
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (grants.values.all { it }) action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark app: force transparent bars with LIGHT icons regardless
        // of the system ui-mode (default auto() would pick light icons over
        // our #0B0F14 bars when the phone is in light mode).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            AuroraTheme {
                val vm: AuroraViewModel = viewModel()
                vmRef = vm
                AuroraRoot(
                    vm = vm,
                    requestBluetoothPermission = { onGranted ->
                        val needed = arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                        val missing = needed.filter {
                            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            vm.refreshPermissionState()
                            onGranted()
                        } else {
                            pendingPermissionAction = onGranted
                            permissionLauncher.launch(needed)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuroraRoot(
    vm: AuroraViewModel,
    requestBluetoothPermission: (onGranted: () -> Unit) -> Unit
) {
    val route by vm.route.collectAsState()
    val connState by vm.ble.state.collectAsState()
    val hasPermission = vm.hasPermission
    val snackbarHostState = remember { SnackbarHostState() }

    // Toast-style feedback for commands / errors — ONE host covering every
    // route (Scan and Editor have no Scaffold of their own to host it).
    LaunchedEffect(Unit) {
        vm.toasts.receiveAsFlow().collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val r = route) {
            is AuroraViewModel.Route.Scan -> ScanScreen(
                vm = vm,
                hasBluetoothPermission = hasPermission,
                onRequestPermission = { requestBluetoothPermission { vm.startScan() } }
            )

            is AuroraViewModel.Route.Editor -> {
                BackHandler { vm.backFromEditor() }
                EditorScreen(vm = vm, modeId = r.mode)
            }

            is AuroraViewModel.Route.Main -> {
                val tab = r.tab
                BackHandler(enabled = tab != Tab.CONTROL) { vm.openTab(Tab.CONTROL) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                val name = (connState as? BleManager.ConnectionState.Connected)?.name
                                Text(
                                    if (name != null) "🌌 $name" else "🌌 Aurora",
                                    fontFamily = FontFamily.SansSerif
                                )
                            },
                            actions = {
                                val connected = connState is BleManager.ConnectionState.Connected
                                Text(
                                    if (connected) "● connected" else "○ offline",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (connected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationItem(Tab.CONTROL, tab, Icons.Filled.PlayArrow, "Control", vm)
                            NavigationItem(Tab.MODES, tab, Icons.Filled.Layers, "Modes", vm)
                            NavigationItem(Tab.SETTINGS, tab, Icons.Filled.Settings, "Settings", vm)
                            NavigationItem(Tab.LOG, tab, Icons.Filled.List, "Log", vm)
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            Tab.CONTROL -> ControlScreen(vm)
                            Tab.MODES -> ModesScreen(vm)
                            Tab.SETTINGS -> SettingsScreen(vm)
                            Tab.LOG -> LogScreen(vm)
                        }
                    }
                }
            }
        }

        // Global snackbar — bottom center, above system bars, on all routes.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** RowScope receiver — NavigationBarItem is a RowScope extension in material3. */
@Composable
private fun RowScope.NavigationItem(
    target: Tab,
    current: Tab,
    icon: ImageVector,
    label: String,
    vm: AuroraViewModel
) {
    NavigationBarItem(
        selected = target == current,
        onClick = { vm.openTab(target) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
