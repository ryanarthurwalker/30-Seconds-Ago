package com.thirtysecondsago.thorreplay.capture

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.util.Log
import com.thirtysecondsago.thorreplay.util.LogTags
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PlaybackAudioRecorder(
    private val projection: MediaProjection,
    replayDurationSeconds: Int,
) {
    private val sampleRate = 48_000
    private val channelCount = 2
    private val bytesPerFrame = channelCount * 2
    private val maxDurationUs = replayDurationSeconds * 1_000_000L
    private val buffer = EncodedRingBuffer(
        maxDurationUs = maxDurationUs,
        maxBytes = 8L * 1024L * 1024L,
    )
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var worker: Thread? = null
    private var totalFramesRead = 0L
    @Volatile var unavailableReason: String = ""
        private set

    fun start() {
        if (running.getAndSet(true)) return
        worker = thread(name = "ThorReplayAudioCapture") {
            runCatching { captureLoop() }
                .onFailure {
                    unavailableReason = it.message ?: it.javaClass.simpleName
                    Log.w(LogTags.SERVICE, "Internal audio unavailable: $unavailableReason", it)
                }
            running.set(false)
            release()
        }
    }

    fun snapshot(): EncodedBufferSnapshot = buffer.snapshot()

    fun stop() {
        running.set(false)
        runCatching { audioRecord?.stop() }
        worker?.join(1_000L)
        release()
    }

    private fun captureLoop() {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate / 10 * bytesPerFrame)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBufferBytes * 2)
            .build()
        audioRecord = record

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder = codec
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        record.startRecording()

        val outputInfo = MediaCodec.BufferInfo()
        while (running.get()) {
            feedInput(codec, record)
            drainOutput(codec, outputInfo, endOfStream = false)
        }
        signalEndOfStream(codec)
        drainOutput(codec, outputInfo, endOfStream = true)
    }

    private fun feedInput(codec: MediaCodec, record: AudioRecord) {
        val inputIndex = codec.dequeueInputBuffer(10_000L)
        if (inputIndex < 0) return
        val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
        inputBuffer.clear()
        val bytesRead = record.read(inputBuffer, inputBuffer.capacity())
        if (bytesRead <= 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs(), 0)
            return
        }
        val ptsUs = presentationTimeUs()
        totalFramesRead += bytesRead / bytesPerFrame
        codec.queueInputBuffer(inputIndex, 0, bytesRead, ptsUs, 0)
    }

    private fun signalEndOfStream(codec: MediaCodec) {
        val inputIndex = codec.dequeueInputBuffer(100_000L)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs(),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
        }
    }

    private fun drainOutput(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean,
    ) {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(info, if (endOfStream) 100_000L else 0L)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    buffer.updateOutputFormat(codec.outputFormat)
                }
                outputIndex >= 0 -> {
                    appendOutput(codec, outputIndex, info)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun appendOutput(codec: MediaCodec, outputIndex: Int, info: MediaCodec.BufferInfo) {
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            codec.releaseOutputBuffer(outputIndex, false)
            return
        }
        val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputIndex)
        if (outputBuffer != null && info.size > 0) {
            val bytes = ByteArray(info.size)
            outputBuffer.position(info.offset)
            outputBuffer.limit(info.offset + info.size)
            outputBuffer.get(bytes)
            buffer.append(
                EncodedSample(
                    data = bytes,
                    presentationTimeUs = info.presentationTimeUs,
                    flags = info.flags,
                )
            )
        }
        codec.releaseOutputBuffer(outputIndex, false)
    }

    private fun presentationTimeUs(): Long {
        return totalFramesRead * 1_000_000L / sampleRate
    }

    private fun release() {
        runCatching { audioRecord?.release() }
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        audioRecord = null
        encoder = null
    }
}
