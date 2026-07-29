package com.whiskeymike.wmpoketrap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.whiskeymike.wmpoketrap.bot.BotEngine
import com.whiskeymike.wmpoketrap.bot.SettingsRepository
import com.whiskeymike.wmpoketrap.bot.TrapSettings
import com.whiskeymike.wmpoketrap.bot.WnEngine
import com.whiskeymike.wmpoketrap.service.CalibrationWizardService
import com.whiskeymike.wmpoketrap.service.OverlayService
import com.whiskeymike.wmpoketrap.service.TrapAccessibilityService
import com.whiskeymike.wmpoketrap.ui.CalibrationActivity
import com.whiskeymike.wmpoketrap.ui.MainScreen
import com.whiskeymike.wmpoketrap.ui.theme.WmTheme
import com.whiskeymike.wmpoketrap.update.AppUpdater
import com.whiskeymike.wmpoketrap.update.UpdateCheckResult
import com.whiskeymike.wmpoketrap.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepo: SettingsRepository
    private var permTick by mutableIntStateOf(0)
    private var updateStatus by mutableStateOf("Checking for updates…")
    private var updateBusy by mutableStateOf(false)
    private var updateReady by mutableStateOf(false)
    private var pendingUpdate: UpdateInfo? = null
    private var downloadedApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepo = SettingsRepository(applicationContext)
        val engine = BotEngine.get(this)
        val wnEngine = WnEngine.get(this)

        setContent {
            WmTheme {
                // Read tick so permission flips after returning from Settings
                @Suppress("UNUSED_VARIABLE")
                val refresh = permTick
                val settings by settingsRepo.settingsFlow.collectAsState(initial = TrapSettings())
                val stats by engine.stats.collectAsState()
                val wnStats by wnEngine.stats.collectAsState()

                MainScreen(
                    settings = settings,
                    stats = stats,
                    wnStats = wnStats,
                    accessibilityOn = TrapAccessibilityService.isEnabled(),
                    overlayOn = Settings.canDrawOverlays(this),
                    onSave = { s -> lifecycleScope.launch { settingsRepo.save(s) } },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlayPermission = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    },
                    onStartOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        } else {
                            startForegroundService(Intent(this, OverlayService::class.java))
                        }
                    },
                    onStopOverlay = {
                        stopService(Intent(this, OverlayService::class.java))
                    },
                    onStartBot = { engine.start() },
                    onPauseBot = { engine.togglePause() },
                    onStopBot = { engine.stop() },
                    onStartWn = { wnEngine.start() },
                    onPauseWn = { wnEngine.togglePause() },
                    onStopWn = { wnEngine.stop() },
                    onCalibrate = { key ->
                        startActivity(Intent(this, CalibrationActivity::class.java).putExtra("key", key))
                    },
                    onStartCalibrationWizard = {
                        if (!Settings.canDrawOverlays(this)) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        } else {
                            CalibrationWizardService.start(this)
                            moveTaskToBack(true)
                        }
                    },
                    onStartWnCalibrationWizard = {
                        if (!Settings.canDrawOverlays(this)) {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ),
                            )
                        } else {
                            CalibrationWizardService.start(this, wnOnly = true)
                            moveTaskToBack(true)
                        }
                    },
                    updateStatus = updateStatus,
                    updateBusy = updateBusy,
                    updateReady = updateReady,
                    onCheckUpdate = { checkForUpdate(forceToast = true) },
                    onInstallUpdate = { installPendingUpdate() },
                )
            }
        }

        checkForUpdate(forceToast = false)
    }

    private fun checkForUpdate(forceToast: Boolean) {
        if (updateBusy) return
        updateBusy = true
        updateReady = false
        updateStatus = "Checking GitHub for updates…"
        lifecycleScope.launch {
            when (val result = AppUpdater.check()) {
                is UpdateCheckResult.Available -> {
                    pendingUpdate = result.info
                    updateStatus =
                        "Update ${result.info.versionName} available — downloading…"
                    try {
                        downloadedApk = AppUpdater.downloadApk(this@MainActivity, result.info)
                        updateReady = true
                        updateStatus =
                            "Update ${result.info.versionName} ready — tap Install Update"
                        if (forceToast) {
                            Toast.makeText(
                                this@MainActivity,
                                "Update ready",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        updateReady = false
                        updateStatus = "Download failed: ${e.message}"
                    }
                }
                UpdateCheckResult.UpToDate -> {
                    pendingUpdate = null
                    downloadedApk = null
                    updateStatus = "You're on the latest version (${BuildConfig.VERSION_NAME})"
                    if (forceToast) {
                        Toast.makeText(
                            this@MainActivity,
                            "Already up to date",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                is UpdateCheckResult.Failed -> {
                    updateStatus = "Update check: ${result.message}"
                    if (forceToast) {
                        Toast.makeText(
                            this@MainActivity,
                            result.message,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            updateBusy = false
        }
    }

    private fun installPendingUpdate() {
        val apk = downloadedApk
        if (apk == null || !apk.exists()) {
            updateStatus = "No update file yet — tap Check Update"
            return
        }
        if (!AppUpdater.canRequestInstall(this)) {
            updateStatus = "Allow install permission, then tap Install Update again"
            AppUpdater.openInstallPermissionSettings(this)
            return
        }
        AppUpdater.installApk(this, apk)
        updateStatus = "Android install screen opened — confirm Update"
    }

    override fun onResume() {
        super.onResume()
        permTick++
    }
}
