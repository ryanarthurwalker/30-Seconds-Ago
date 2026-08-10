package com.thirtysecondsago.thorreplay.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.thirtysecondsago.thorreplay.display.DisplayIndicatorService
import com.thirtysecondsago.thorreplay.settings.AppSettings
import com.thirtysecondsago.thorreplay.settings.CaptureState
import com.thirtysecondsago.thorreplay.settings.SettingsRepository
import com.thirtysecondsago.thorreplay.storage.ReplayStorage
import com.thirtysecondsago.thorreplay.util.LogTags
import com.thirtysecondsago.thorreplay.util.NotificationHelper
import com.thirtysecondsago.thorreplay.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

class ReplayBufferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepository: SettingsRepository
    private var active = false
    private var recorder: BufferedScreenRecorder? = null
    private var projection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        NotificationHelper.ensureChannels(this)
        Log.i(LogTags.SERVICE, "ReplayBufferService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                if (intent == null) {
                    scope.launch {
                        settingsRepository.updateCaptureState(CaptureState.Error, "Missing screen capture permission")
                    }
                    stopSelf()
                } else {
                    startReplayBuffer(intent)
                }
            }
            ACTION_STOP -> stopReplayBuffer()
            ACTION_SAVE_REPLAY -> requestReplaySave()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        val stoppedUnexpectedly = active
        active = false
        stopRecorder(projectionAlreadyStopped = false)
        if (stoppedUnexpectedly) {
            runBlocking(Dispatchers.IO) {
                settingsRepository.updateCaptureState(
                    CaptureState.Error,
                    "Recorder service stopped unexpectedly",
                )
            }
        }
        scope.cancel()
        Log.i(LogTags.SERVICE, "ReplayBufferService destroyed")
        super.onDestroy()
    }

    private fun startReplayBuffer(intent: Intent) {
        if (active) {
            Log.w(LogTags.SERVICE, "Ignoring duplicate start request")
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            scope.launch {
                settingsRepository.updateCaptureState(CaptureState.Error, "Missing screen capture permission")
            }
            Log.e(LogTags.SERVICE, "Start requested without MediaProjection result")
            stopSelf()
            return
        }

        active = true
        ServiceCompat.startForeground(
            this,
            NotificationHelper.CAPTURE_NOTIFICATION_ID,
            NotificationHelper.captureNotification(this, active = true),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            },
        )

        scope.launch {
            runCatching {
                settingsRepository.updateCaptureState(CaptureState.Starting, "Preparing encoder and replay buffer")
                val settings = settingsRepository.settings.first()
                val manager = getSystemService(MediaProjectionManager::class.java)
                val mediaProjection = manager.getMediaProjection(resultCode, resultData)
                val bufferedRecorder = BufferedScreenRecorder(
                    context = applicationContext,
                    projection = mediaProjection,
                    configuration = settings.toCaptureConfiguration(),
                    onProjectionStopped = {
                        scope.launch(Dispatchers.IO) {
                            val stoppedUnexpectedly = active
                            active = false
                            stopRecorder(projectionAlreadyStopped = true)
                            if (stoppedUnexpectedly) {
                                settingsRepository.updateCaptureState(
                                    CaptureState.Error,
                                    "Screen capture permission ended unexpectedly",
                                )
                            }
                            ServiceCompat.stopForeground(this@ReplayBufferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    },
                )
                projection = mediaProjection
                recorder = bufferedRecorder
                bufferedRecorder.start()
                settingsRepository.updateCaptureState(
                    CaptureState.Ready,
                    "Buffering last ${bufferedRecorder.replayDurationSeconds}s at ${bufferedRecorder.width} x ${bufferedRecorder.height}"
                )
            }.onFailure { error ->
                Log.e(LogTags.SERVICE, "Unable to start replay buffer", error)
                active = false
                settingsRepository.updateCaptureState(
                    CaptureState.Error,
                    "Replay buffer failed: ${error.message ?: error.javaClass.simpleName}",
                )
                ServiceCompat.stopForeground(this@ReplayBufferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        Log.i(LogTags.SERVICE, "Replay buffer start requested")
    }

    private fun stopReplayBuffer() {
        active = false
        scope.launch(Dispatchers.IO) {
            stopRecorder(projectionAlreadyStopped = false)
            settingsRepository.updateCaptureState(CaptureState.Off, "Replay buffer stopped")
            ServiceCompat.stopForeground(this@ReplayBufferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestReplaySave() {
        if (!active) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.CAPTURE_NOTIFICATION_ID,
                NotificationHelper.captureNotification(this, active = false),
                0,
            )
            val reason = "Replay buffer is not active"
            scope.launch { settingsRepository.updateCaptureState(CaptureState.Error, reason) }
            NotificationHelper.notifySaveResult(this, "Replay not saved", reason)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        scope.launch(Dispatchers.IO) { saveReplay() }
    }

    private fun stopRecorder(projectionAlreadyStopped: Boolean) {
        val stoppedRecorder = recorder
        val stoppedProjection = projection
        recorder = null
        projection = null

        runCatching { stoppedRecorder?.stop(signalEndOfInput = !projectionAlreadyStopped) }
            .onFailure { Log.e(LogTags.SERVICE, "Unable to stop replay buffer cleanly", it) }
        if (!projectionAlreadyStopped) {
            runCatching { stoppedProjection?.stop() }
                .onFailure { Log.w(LogTags.SERVICE, "MediaProjection stop failed", it) }
        }
    }

    private suspend fun saveReplay() {
        val activeRecorder = recorder
        if (activeRecorder == null) {
            val reason = "Replay buffer is not active"
            settingsRepository.updateCaptureState(CaptureState.Error, reason)
            NotificationHelper.notifySaveResult(this, "Replay not saved", reason)
            return
        }

        val settings = settingsRepository.settings.first()
        settingsRepository.updateCaptureState(CaptureState.Saving, "Saving the most recent replay")
        delay(350L)
        val snapshot = activeRecorder.snapshot()
        val audioSnapshot = activeRecorder.audioSnapshot()
        val outputFormat = snapshot.outputFormat
        if (outputFormat == null) {
            val reason = "Encoder format is not ready yet"
            settingsRepository.updateCaptureState(CaptureState.Error, reason, bufferActive = true)
            NotificationHelper.notifySaveResult(this, "Replay not saved", reason)
            return
        }

        val selectedSamples = ReplayMuxer.selectSamplesForReplay(
            snapshot,
            audioSnapshot,
            settings.replayDurationSeconds * 1_000_000L,
        )
        if (selectedSamples.videoSamples.isEmpty()) {
            val reason = "No keyframe available in buffer yet"
            settingsRepository.updateCaptureState(CaptureState.Error, reason, bufferActive = true)
            NotificationHelper.notifySaveResult(this, "Replay not saved", reason)
            return
        }

        val filename = ReplayStorage.filenameFor(
            timestamp = LocalDateTime.now(),
            template = settings.filenameTemplate,
            durationSeconds = settings.replayDurationSeconds,
            width = settings.width,
            height = settings.height,
            frameRate = settings.frameRate,
        )
        val tempFile = ReplayStorage.createTempRecordingFile(applicationContext, filename)
        runCatching {
            ReplayMuxer.writeReplayToFile(
                outputFormat = outputFormat,
                samples = selectedSamples.videoSamples,
                outputFile = tempFile,
                audioFormat = audioSnapshot?.outputFormat,
                audioSamples = selectedSamples.audioSamples,
            )
            val savedVideo = ReplayStorage.saveCompletedVideo(
                applicationContext,
                tempFile,
                filename,
                settings.outputFolderUri,
            )
            tempFile.delete()
            settingsRepository.updateLastSavedClip(filename, savedVideo.uri.toString())
            settingsRepository.updateCaptureState(
                CaptureState.Ready,
                "Saved ${selectedSamples.videoSamples.durationSeconds()}s replay; audio samples ${selectedSamples.audioSamples.size}"
            )
            VibrationHelper.shortConfirm(this)
            NotificationHelper.notifySaveResult(this, "Replay saved", filename)
            showSavedOverlay(settings.savedPopupDisplayId, filename)
            Log.i(
                LogTags.STORAGE,
                "Saved replay uri=${savedVideo.uri} videoSamples=${selectedSamples.videoSamples.size} audioSamples=${selectedSamples.audioSamples.size}"
            )
        }.onFailure { error ->
            tempFile.delete()
            val reason = "Replay save failed: ${error.message ?: error.javaClass.simpleName}"
            Log.e(LogTags.STORAGE, reason, error)
            settingsRepository.updateCaptureState(CaptureState.Error, reason, bufferActive = true)
            settingsRepository.updateLastSavedClip("Replay failed", "")
            NotificationHelper.notifySaveResult(this, "Replay failed", reason)
        }
    }

    private fun AppSettings.toCaptureConfiguration(): CaptureConfiguration {
        return CaptureConfiguration(
            width = width,
            height = height,
            frameRate = frameRate,
            videoBitrate = bitrateMbps * 1_000_000,
            replayDurationSeconds = replayDurationSeconds,
            audioEnabled = audioEnabled,
        )
    }

    private fun List<EncodedSample>.durationSeconds(): Long {
        if (isEmpty()) return 0
        return ((last().presentationTimeUs - first().presentationTimeUs) / 1_000_000L).coerceAtLeast(0)
    }

    private fun showSavedOverlay(displayId: Int, filename: String) {
        runCatching {
            startService(DisplayIndicatorService.savedPopupIntent(this, displayId, filename))
        }.onFailure {
            Log.w(LogTags.SERVICE, "Unable to show saved overlay", it)
        }
    }

    companion object {
        const val ACTION_START = "com.thirtysecondsago.thorreplay.action.START"
        const val ACTION_STOP = "com.thirtysecondsago.thorreplay.action.STOP"
        const val ACTION_SAVE_REPLAY = "com.thirtysecondsago.thorreplay.action.SAVE_REPLAY"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun commandIntent(context: Context, action: String): Intent {
            return Intent(context, ReplayBufferService::class.java).setAction(action)
        }

        fun projectionIntent(context: Context, resultCode: Int, resultData: Intent): Intent {
            return commandIntent(context, ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
        }
    }
}
