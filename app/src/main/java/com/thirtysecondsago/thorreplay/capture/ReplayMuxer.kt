package com.thirtysecondsago.thorreplay.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object ReplayMuxer {
    data class ReplaySelection(
        val videoSamples: List<EncodedSample>,
        val audioSamples: List<EncodedSample>,
    )

    fun selectSamplesForReplay(
        snapshot: EncodedBufferSnapshot,
        requestedDurationUs: Long,
    ): List<EncodedSample> {
        if (snapshot.samples.isEmpty()) return emptyList()
        val newestTimeUs = snapshot.samples.last().presentationTimeUs
        val desiredStartUs = newestTimeUs - requestedDurationUs
        val startIndex = snapshot.samples.indexOfLast {
            it.presentationTimeUs <= desiredStartUs &&
                it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        }.takeIf { it >= 0 } ?: snapshot.samples.indexOfFirst {
            it.presentationTimeUs >= desiredStartUs &&
                it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        }
        if (startIndex < 0) return emptyList()
        return normalizeTimestamps(snapshot.samples.drop(startIndex))
    }

    fun selectSamplesForReplay(
        videoSnapshot: EncodedBufferSnapshot,
        audioSnapshot: EncodedBufferSnapshot?,
        requestedDurationUs: Long,
    ): ReplaySelection {
        if (videoSnapshot.samples.isEmpty()) return ReplaySelection(emptyList(), emptyList())
        val newestTimeUs = videoSnapshot.samples.last().presentationTimeUs
        val desiredStartUs = newestTimeUs - requestedDurationUs
        val startIndex = videoSnapshot.samples.indexOfLast {
            it.presentationTimeUs <= desiredStartUs &&
                it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        }.takeIf { it >= 0 } ?: videoSnapshot.samples.indexOfFirst {
            it.presentationTimeUs >= desiredStartUs &&
                it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        }
        if (startIndex < 0) return ReplaySelection(emptyList(), emptyList())
        val startPtsUs = videoSnapshot.samples[startIndex].presentationTimeUs
        val selectedVideo = normalizeTimestamps(videoSnapshot.samples.drop(startIndex), startPtsUs)
        val selectedAudio = audioSnapshot?.samples
            ?.filter { it.presentationTimeUs >= startPtsUs && it.presentationTimeUs <= newestTimeUs }
            ?.let { normalizeTimestamps(it, startPtsUs) }
            ?: emptyList()
        return ReplaySelection(selectedVideo, selectedAudio)
    }

    fun normalizeTimestamps(samples: List<EncodedSample>): List<EncodedSample> {
        val firstPts = samples.firstOrNull()?.presentationTimeUs ?: return emptyList()
        return normalizeTimestamps(samples, firstPts)
    }

    private fun normalizeTimestamps(samples: List<EncodedSample>, basePtsUs: Long): List<EncodedSample> {
        var lastPts = -1L
        return samples.map { sample ->
            val normalized = (sample.presentationTimeUs - basePtsUs).coerceAtLeast(lastPts + 1)
            lastPts = normalized
            sample.copy(presentationTimeUs = normalized)
        }
    }

    fun writeReplayToFile(
        outputFormat: MediaFormat,
        samples: List<EncodedSample>,
        outputFile: File,
        audioFormat: MediaFormat? = null,
        audioSamples: List<EncodedSample> = emptyList(),
    ) {
        require(samples.isNotEmpty()) { "No buffered video samples available" }
        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(outputFormat)
            val audioTrackIndex = if (audioFormat != null && audioSamples.isNotEmpty()) {
                muxer.addTrack(audioFormat)
            } else {
                -1
            }
            muxer.start()
            samples.forEach { sample ->
                val info = MediaCodec.BufferInfo().apply {
                    set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
                }
                muxer.writeSampleData(trackIndex, ByteBuffer.wrap(sample.data), info)
            }
            if (audioTrackIndex >= 0) {
                audioSamples.forEach { sample ->
                    val info = MediaCodec.BufferInfo().apply {
                        set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
                    }
                    muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(sample.data), info)
                }
            }
            muxer.stop()
        } finally {
            muxer?.release()
        }
    }
}
