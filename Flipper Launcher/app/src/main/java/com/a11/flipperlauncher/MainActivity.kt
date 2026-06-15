package com.a11.flipperlauncher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.a11.flipperlauncher.exec.TermuxBridge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.a11.flipperlauncher.ui.LauncherRoot
import com.a11.flipperlauncher.ui.theme.FlipperTheme
import com.a11.flipperlauncher.vm.LauncherViewModel

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { intent?.let(::applyBattery) }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { vm.refreshApps() }
    }

    /** Receives Termux RUN_COMMAND results (stdout/stderr/exit) for the `lx` bridge. */
    private val termuxResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val b = intent?.getBundleExtra(TermuxBridge.RESULT_BUNDLE) ?: return
            vm.onLinuxResult(
                stdout = b.getString(TermuxBridge.KEY_STDOUT).orEmpty(),
                stderr = b.getString(TermuxBridge.KEY_STDERR).orEmpty(),
                exit = b.getInt(TermuxBridge.KEY_EXIT, 0),
                err = b.getInt(TermuxBridge.KEY_ERR, 0),
                errmsg = b.getString(TermuxBridge.KEY_ERRMSG).orEmpty(),
            )
        }
    }

    private val bindWidgetLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            vm.onBindResult(result.resultCode == RESULT_OK)
        }

    // Android 11+ "All files access" returns no result code — re-check on return.
    private val allFilesAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.onStorageGrantResult()
        }

    // Pre-11 path: the legacy runtime storage permission.
    private val legacyStorageLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onStorageGrantResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlipperTheme(accentName = vm.accent) {
                LauncherRoot(vm)
                WidgetFlowEffects()
                StorageFlowEffect()
            }
        }
    }

    /** Drives the two system hand-offs a widget needs: bind permission, then config. */
    @Composable
    private fun WidgetFlowEffects() {
        LaunchedEffect(vm.pendingBind) {
            val p = vm.pendingBind ?: return@LaunchedEffect
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.id)
                .putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                    ComponentName.unflattenFromString(p.component),
                )
            runCatching { bindWidgetLauncher.launch(intent) }
                .onFailure { vm.onBindResult(false) }
        }
        LaunchedEffect(vm.pendingConfig) {
            val id = vm.pendingConfig ?: return@LaunchedEffect
            runCatching {
                vm.widgetHost.host.startAppWidgetConfigureActivityForResult(
                    this@MainActivity, id, 0, REQ_WIDGET_CONFIG, null,
                )
            }.onFailure {
                // Config activity unreachable (not exported, etc.) — widget still works.
                vm.onConfigResult(true)
            }
        }
    }

    /**
     * Routes the code-workspace storage request to the per-version flow. The state
     * stays set until a result callback clears it, so we never null it here (that
     * would re-key the effect and cancel the launch mid-flight).
     */
    @Composable
    private fun StorageFlowEffect() {
        LaunchedEffect(vm.pendingStorageRequest) {
            if (!vm.pendingStorageRequest) return@LaunchedEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: All-files access. Prefer the per-app screen; fall
                // back to the global list if the device hides the direct route.
                val perApp = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
                runCatching { allFilesAccessLauncher.launch(perApp) }.onFailure {
                    runCatching {
                        allFilesAccessLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }.onFailure { vm.onStorageGrantResult() }
                }
            } else {
                runCatching {
                    legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }.onFailure { vm.onStorageGrantResult() }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WIDGET_CONFIG) vm.onConfigResult(resultCode == RESULT_OK)
    }

    override fun onStart() {
        super.onStart()
        // Battery is a protected sticky system broadcast. AndroidX's NOT_EXPORTED
        // wrapper adds a dynamic permission that blocks updates on Samsung Android 12.
        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let(::applyBattery)

        // Refresh the app list whenever something is installed or removed.
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, pkgFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        ContextCompat.registerReceiver(
            this, termuxResultReceiver,
            IntentFilter(TermuxBridge.RESULT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        vm.refreshApps()
        vm.widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        vm.widgetHost.stopListening()
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(termuxResultReceiver) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME pressed while we're already the foreground launcher.
        vm.requestHome()
    }

    private fun applyBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        vm.updateBattery(pct, charging)
    }

    companion object {
        private const val REQ_WIDGET_CONFIG = 9101
    }
}
