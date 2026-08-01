package com.thirtysecondsago.thorreplay.capture

import android.media.MediaFormat
import java.util.ArrayDeque

data class EncodedSample(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int,
)

data class EncodedBufferSnapshot(
    val samples: List<EncodedSample>,
    val approximateSizeBytes: Long,
    val outputFormat: MediaFormat? = null,
)

class EncodedRingBuffer(
    private val maxDurationUs: Long,
    private val maxBytes: Long,
) {
    private val samples = ArrayDeque<EncodedSample>()
    private var byteCount = 0L
    private var outputFormat: MediaFormat? = null

    @Synchronized
    fun updateOutputFormat(format: MediaFormat) {
        outputFormat = format
    }

    @Synchronized
    fun append(sample: EncodedSample) {
        samples.addLast(sample)
        byteCount += sample.data.size
        evictOldSamples()
    }

    @Synchronized
    fun snapshot(): EncodedBufferSnapshot {
        return EncodedBufferSnapshot(samples.toList(), byteCount, outputFormat)
    }

    @Synchronized
    fun clear() {
        samples.clear()
        byteCount = 0
    }

    @Synchronized
    fun durationUs(): Long {
        val first = samples.firstOrNull()?.presentationTimeUs ?: return 0
        val last = samples.lastOrNull()?.presentationTimeUs ?: return 0
        return (last - first).coerceAtLeast(0)
    }

    @Synchronized
    fun sizeBytes(): Long = byteCount

    private fun evictOldSamples() {
        while (samples.size > 1) {
            val first = samples.first()
            val last = samples.last()
            val tooOld = last.presentationTimeUs - first.presentationTimeUs > maxDurationUs
            val tooLarge = byteCount > maxBytes
            if (!tooOld && !tooLarge) break
            samples.removeFirst()
            byteCount -= first.data.size
        }
    }
}
