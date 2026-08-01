package com.thirtysecondsago.thorreplay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.thirtysecondsago.thorreplay.capture.ReplayBufferService
import com.thirtysecondsago.thorreplay.display.DisplayIndicatorService
import com.thirtysecondsago.thorreplay.display.DisplayOption
import com.thirtysecondsago.thorreplay.input.KeyBindingRepository
import com.thirtysecondsago.thorreplay.input.KeyCaptureEvent
import com.thirtysecondsago.thorreplay.input.toCaptureEvent
import com.thirtysecondsago.thorreplay.settings.SettingsRepository
import com.thirtysecondsago.thorreplay.storage.ReplayStorage
import com.thirtysecondsago.thorreplay.storage.SavedClip
import com.thirtysecondsago.thorreplay.ui.ThorReplayApp
import com.thirtysecondsago.thorreplay.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var keyBindingRepository: KeyBindingRepository
    private var keyDetectionEnabled = false
    private var onDetectedKey: ((KeyCaptureEvent) -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startReplayService(
                    ReplayBufferService.projectionIntent(
                        this,
                        result.resultCode,
                        result.data ?: return@registerForActivityResult,
                    )
                )
            } else {
                scope.launch {
                    SettingsRepository(applicationContext).updateServiceStatus("Screen capture permission denied")
                }
            }
        }
    private val outputFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                persistOutputFolder(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        maybeRequestNotificationPermission()
        maybeRequestAudioPermission()
        settingsRepository = SettingsRepository(applicationContext)
        keyBindingRepository = KeyBindingRepository(applicationContext)

        setContent {
            ThorReplayApp(
                settingsRepository = settingsRepository,
                keyBindingRepository = keyBindingRepository,
                onStartBuffer = ::requestScreenCapture,
                onStopBuffer = { startReplayService(ReplayBufferService.ACTION_STOP) },
                onSaveReplay = { startReplayService(ReplayBufferService.ACTION_SAVE_REPLAY) },
                onChooseOutputFolder = ::chooseOutputFolder,
                getDisplayOptions = ::getDisplayOptions,
                onSelectDisplay = ::selectDisplay,
                onSelectSavedPopupDisplay = ::selectSavedPopupDisplay,
                onShowDisplayIndicator = ::showDisplayIndicator,
                onHideDisplayIndicator = ::hideDisplayIndicator,
                onOpenOverlaySettings = ::openOverlaySettings,
                onLoadSavedClips = ::loadSavedClips,
                onOpenClip = ::openClip,
                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                onKeyDetectionActive = { active, callback ->
                    keyDetectionEnabled = active
                    onDetectedKey = callback
                },
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyDetectionEnabled && event.action == KeyEvent.ACTION_UP) {
            val callback = onDetectedKey
            if (callback != null) {
                val captureEvent = event.toCaptureEvent()
                callback(captureEvent)
                scope.launch { keyBindingRepository.saveKey(captureEvent) }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startReplayService(action: String) {
        startReplayService(ReplayBufferService.commandIntent(this, action))
    }

    private fun startReplayService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun loadSavedClips(outputFolderUri: String): List<SavedClip> {
        return ReplayStorage.listSavedClips(applicationContext, outputFolderUri)
    }

    private fun openClip(clip: SavedClip) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(clip.uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Open clip"))
    }

    private fun chooseOutputFolder() {
        outputFolderLauncher.launch(null)
    }

    private fun persistOutputFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
        val label = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { uri.toString() }
            ?: uri.toString()
        scope.launch {
            settingsRepository.updateOutputFolder(uri.toString(), label)
        }
    }

    private fun getDisplayOptions(): List<DisplayOption> {
        return getSystemService(DisplayManager::class.java).displays.map { display ->
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            DisplayOption(
                displayId = display.displayId,
                label = "Display ${display.displayId}: ${metrics.widthPixels} x ${metrics.heightPixels}",
            )
        }
    }

    private fun selectDisplay(option: DisplayOption) {
        scope.launch {
            settingsRepository.updateSelectedDisplay(option.displayId, option.label)
        }
    }

    private fun selectSavedPopupDisplay(option: DisplayOption) {
        scope.launch {
            settingsRepository.updateSavedPopupDisplay(option.displayId, option.label)
        }
    }

    private fun showDisplayIndicator(displayId: Int) {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        startService(DisplayIndicatorService.showIntent(this, displayId))
    }

    private fun hideDisplayIndicator() {
        startService(DisplayIndicatorService.hideIntent(this))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeRequestAudioPermission() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
