package com.thirtysecondsago.thorreplay.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.thirtysecondsago.thorreplay.util.LogTags
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DirectScreenRecorder(
    private val context: Context,
    private val projection: MediaProjection,
    private val configuration: CaptureConfiguration,
    private val outputFile: File,
    private val onProjectionStopped: () -> Unit,
) {
    private val encoderThread = HandlerThread("ThorReplayVideoEncoder")
    private val projectionCallbackThread = HandlerThread("ThorReplayProjection")
    private val finished = CountDownLatch(1)
    private val lock = Any()

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var stopping = false
    private var completed = false
    private var firstPresentationTimeUs = -1L
    private var projectionCallback: MediaProjection.Callback? = null
    private var samplesWritten = 0L
    private var muxerStopSucceeded = false

    val width: Int = configuration.width
    val height: Int = configuration.height
    val frameRate: Int = configuration.frameRate
    val bitrate: Int = configuration.videoBitrate
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
        val videoMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = videoMuxer
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
            "ThorReplayDirectRecording",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            Handler(projectionCallbackThread.looper),
        )
        videoEncoder.start()
        Log.i(LogTags.SERVICE, "Direct recording started encoder=$encoderName ${width}x$height@$frameRate")
    }

    fun stop(timeoutMs: Long = 5_000L, signalEndOfInput: Boolean = true): RecordingStopResult {
        val codec = synchronized(lock) {
            if (stopping) return@synchronized encoder
            stopping = true
            encoder
        }
        if (signalEndOfInput) {
            runCatching { codec?.signalEndOfInputStream() }
                .onFailure { Log.w(LogTags.SERVICE, "Unable to signal encoder EOS", it) }
        } else {
            finished.countDown()
        }
        val cleanFinish = finished.await(timeoutMs, TimeUnit.MILLISECONDS)
        val hadVideoTrack = muxerStarted
        release()
        return RecordingStopResult(
            completedEndOfStream = cleanFinish,
            muxerStarted = hadVideoTrack,
            samplesWritten = samplesWritten,
            muxerStopSucceeded = muxerStopSucceeded,
        )
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
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        muxerStopSucceeded = if (muxerStarted && samplesWritten > 0) {
            runCatching { muxer?.stop() }
                .onFailure { Log.w(LogTags.SERVICE, "Muxer stop failed", it) }
                .isSuccess
        } else {
            false
        }
        runCatching { muxer?.release() }
        encoder = null
        muxer = null
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

            val outputBuffer = codec.getOutputBuffer(index)
            val localMuxer = muxer
            if (outputBuffer != null && localMuxer != null && muxerStarted && info.size > 0) {
                if (firstPresentationTimeUs < 0L) firstPresentationTimeUs = info.presentationTimeUs
                info.presentationTimeUs = (info.presentationTimeUs - firstPresentationTimeUs).coerceAtLeast(0)
                outputBuffer.position(info.offset)
                outputBuffer.limit(info.offset + info.size)
                localMuxer.writeSampleData(trackIndex, outputBuffer, info)
                samplesWritten += 1
            }

            val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            codec.releaseOutputBuffer(index, false)
            if (endOfStream) {
                finished.countDown()
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(LogTags.SERVICE, "Video encoder error", e)
            finished.countDown()
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            synchronized(lock) {
                if (muxerStarted) return
                val localMuxer = muxer ?: return
                trackIndex = localMuxer.addTrack(format)
                localMuxer.start()
                muxerStarted = true
            }
        }
    }
}

data class RecordingStopResult(
    val completedEndOfStream: Boolean,
    val muxerStarted: Boolean,
    val samplesWritten: Long,
    val muxerStopSucceeded: Boolean,
) {
    val isPlayableCandidate: Boolean
        get() = completedEndOfStream && muxerStarted && samplesWritten > 0 && muxerStopSucceeded

    fun failureReason(): String {
        return when {
            !muxerStarted -> "No video track was created"
            samplesWritten == 0L -> "No video frames were written"
            !completedEndOfStream -> "Encoder did not finish before timeout"
            !muxerStopSucceeded -> "MP4 finalization failed"
            else -> "Unknown recording failure"
        }
    }
}
