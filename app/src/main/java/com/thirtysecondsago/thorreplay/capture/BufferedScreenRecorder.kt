package com.thirtysecondsago.thorreplay.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.thirtysecondsago.thorreplay.util.LogTags
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BufferedScreenRecorder(
    private val context: Context,
    private val projection: MediaProjection,
    private val configuration: CaptureConfiguration,
    private val onProjectionStopped: () -> Unit,
) {
    private val encoderThread = HandlerThread("ThorReplayBufferEncoder")
    private val projectionCallbackThread = HandlerThread("ThorReplayProjection")
    private val stopped = CountDownLatch(1)
    private val lock = Any()
    private val bufferHeadroomUs = 2_000_000L
    private val maxDurationUs = configuration.replayDurationSeconds * 1_000_000L + bufferHeadroomUs
    private val maxBytes = ((configuration.videoBitrate / 8L) * configuration.replayDurationSeconds * 3L)
        .coerceIn(32L * 1024L * 1024L, 256L * 1024L * 1024L)
    private val buffer = EncodedRingBuffer(maxDurationUs = maxDurationUs, maxBytes = maxBytes)

    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var stopping = false
    private var completed = false
    private var framesEncoded = 0L
    private var firstVideoPtsUs = -1L
    private var audioRecorder: PlaybackAudioRecorder? = null

    val width: Int = configuration.width
    val height: Int = configuration.height
    val frameRate: Int = configuration.frameRate
    val bitrate: Int = configuration.videoBitrate
    val replayDurationSeconds: Int = configuration.replayDurationSeconds
    var encoderName: String = "Unknown"
        private set

    fun start() {
        encoderThread.start()
        projectionCallbackThread.start()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoderName = videoEncoder.name
        encoder = videoEncoder
        videoEncoder.setCallback(EncoderCallback(), Handler(encoderThread.looper))
        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoEncoder.createInputSurface()

        projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(LogTags.SERVICE, "MediaProjection stopped")
                onProjectionStopped()
            }
        }
        projection.registerCallback(projectionCallback!!, Handler(projectionCallbackThread.looper))

        val densityDpi = context.resources.displayMetrics.densityDpi
        virtualDisplay = projection.createVirtualDisplay(
            "ThorReplayBuffer",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            Handler(projectionCallbackThread.looper),
        )
        videoEncoder.start()
        if (configuration.audioEnabled) {
            audioRecorder = PlaybackAudioRecorder(projection, configuration.replayDurationSeconds + 2).also {
                it.start()
            }
        }
        Log.i(LogTags.SERVICE, "Replay buffer started encoder=$encoderName ${width}x$height@$frameRate")
    }

    fun snapshot(): EncodedBufferSnapshot = buffer.snapshot()

    fun audioSnapshot(): EncodedBufferSnapshot? = audioRecorder?.snapshot()

    fun bufferDurationUs(): Long = buffer.durationUs()

    fun bufferSizeBytes(): Long = buffer.sizeBytes()

    fun framesEncoded(): Long = framesEncoded

    fun stop(timeoutMs: Long = 2_000L, signalEndOfInput: Boolean = true) {
        val codec = synchronized(lock) {
            if (stopping) return
            stopping = true
            encoder
        }
        if (signalEndOfInput) {
            runCatching { codec?.signalEndOfInputStream() }
        } else {
            stopped.countDown()
        }
        stopped.await(timeoutMs, TimeUnit.MILLISECONDS)
        release()
    }

    private fun release() {
        synchronized(lock) {
            if (completed) return
            completed = true
        }
        projectionCallback?.let { callback ->
            runCatching { projection.unregisterCallback(callback) }
        }
        projectionCallback = null
        runCatching { virtualDisplay?.release() }
        runCatching { inputSurface?.release() }
        runCatching { audioRecorder?.stop() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        audioRecorder = null
        encoder = null
        inputSurface = null
        virtualDisplay = null
        encoderThread.quitSafely()
        projectionCallbackThread.quitSafely()
    }

    private inner class EncoderCallback : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo,
        ) {
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                codec.releaseOutputBuffer(index, false)
                return
            }
            val output = codec.getOutputBuffer(index)
            if (output != null && info.size > 0) {
                if (firstVideoPtsUs < 0L) firstVideoPtsUs = info.presentationTimeUs
                val bytes = ByteArray(info.size)
                output.position(info.offset)
                output.limit(info.offset + info.size)
                output.get(bytes)
                buffer.append(
                    EncodedSample(
                        data = bytes,
                        presentationTimeUs = (info.presentationTimeUs - firstVideoPtsUs).coerceAtLeast(0),
                        flags = info.flags,
                    )
                )
                framesEncoded += 1
            }
            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            codec.releaseOutputBuffer(index, false)
            if (eos) stopped.countDown()
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(LogTags.SERVICE, "Replay buffer encoder error", e)
            stopped.countDown()
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            buffer.updateOutputFormat(format)
        }
    }
}
