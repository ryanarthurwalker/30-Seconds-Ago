package com.thirtysecondsago.thorreplay

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
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
import com.thirtysecondsago.thorreplay.settings.CaptureState
import com.thirtysecondsago.thorreplay.storage.ReplayStorage
import com.thirtysecondsago.thorreplay.storage.SavedClip
import com.thirtysecondsago.thorreplay.storage.TrimClipResult
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
    private var controllerInputGuardUntilMs = 0L
    private val guardedControllerKeys = mutableSetOf<Int>()

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
                    SettingsRepository(applicationContext).updateCaptureState(
                        CaptureState.Error,
                        "Screen capture permission denied",
                    )
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
                onShareClip = ::shareClip,
                onRenameClip = ::renameClip,
                onDeleteClip = ::deleteClip,
                onTrimClip = ::trimClip,
                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                onKeyDetectionActive = { active, callback ->
                    keyDetectionEnabled = active
                    onDetectedKey = callback
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        controllerInputGuardUntilMs = SystemClock.uptimeMillis() + CONTROLLER_INPUT_GUARD_MS
        guardedControllerKeys.clear()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shouldGuardControllerActivation(event)) {
            return true
        }
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

    private fun shouldGuardControllerActivation(event: KeyEvent): Boolean {
        val isController = event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_DPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val isActivationKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!isController || !isActivationKey) return false

        if (SystemClock.uptimeMillis() < controllerInputGuardUntilMs) {
            guardedControllerKeys += event.keyCode
            if (event.action == KeyEvent.ACTION_UP) {
                guardedControllerKeys -= event.keyCode
            }
            return true
        }

        if (event.keyCode in guardedControllerKeys) {
            if (event.action == KeyEvent.ACTION_UP) {
                guardedControllerKeys -= event.keyCode
            }
            return true
        }
        return false
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
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(clip.uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newUri(contentResolver, clip.name, clip.uri)
            startActivity(Intent.createChooser(intent, "Open clip in another app"))
        }.onFailure {
            scope.launch {
                settingsRepository.updateServiceStatus("Unable to open clip: No video player found")
            }
        }
    }

    private fun shareClip(clip: SavedClip) {
        runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND)
                .setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, clip.uri)
                .putExtra(Intent.EXTRA_TITLE, clip.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.clipData = ClipData.newUri(contentResolver, clip.name, clip.uri)
            val chooser = Intent.createChooser(shareIntent, "Share clip")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
        }.onFailure {
            scope.launch {
                settingsRepository.updateServiceStatus("Unable to share clip")
            }
        }
    }

    private fun renameClip(clip: SavedClip, newName: String): SavedClip {
        return ReplayStorage.renameClip(applicationContext, clip, newName)
    }

    private fun deleteClip(clip: SavedClip) {
        ReplayStorage.deleteClip(applicationContext, clip)
    }

    private fun trimClip(
        clip: SavedClip,
        startMs: Long,
        endMs: Long,
        replaceOriginal: Boolean,
        outputFolderUri: String,
    ): TrimClipResult {
        return ReplayStorage.trimClip(
            applicationContext,
            clip,
            startMs,
            endMs,
            replaceOriginal,
            outputFolderUri,
        )
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
        val displayManager = getSystemService(DisplayManager::class.java) ?: return emptyList()
        return displayManager.displays.mapNotNull { display ->
            runCatching {
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                val screenName = when (display.displayId) {
                    0 -> "top screen"
                    4 -> "bottom screen"
                    else -> null
                }
                val labelPrefix = if (screenName != null) {
                    "Display ${display.displayId} ($screenName)"
                } else {
                    "Display ${display.displayId}"
                }
                DisplayOption(
                    displayId = display.displayId,
                    label = "$labelPrefix: ${metrics.widthPixels} x ${metrics.heightPixels}",
                )
            }.getOrNull()
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

    private companion object {
        const val CONTROLLER_INPUT_GUARD_MS = 750L
    }
}
