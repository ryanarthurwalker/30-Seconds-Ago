package com.thirtysecondsago.thorreplay.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodedRingBufferTest {
    @Test
    fun evictsSamplesOutsideDuration() {
        val buffer = EncodedRingBuffer(maxDurationUs = 1_000_000, maxBytes = 10_000)

        buffer.append(sample(0, 100))
        buffer.append(sample(500_000, 100))
        buffer.append(sample(1_500_000, 100))

        val snapshot = buffer.snapshot()
        assertEquals(listOf(500_000L, 1_500_000L), snapshot.samples.map { it.presentationTimeUs })
        assertEquals(1_000_000, buffer.durationUs())
    }

    @Test
    fun enforcesMemoryLimit() {
        val buffer = EncodedRingBuffer(maxDurationUs = 10_000_000, maxBytes = 250)

        buffer.append(sample(0, 100))
        buffer.append(sample(1, 100))
        buffer.append(sample(2, 100))

        assertTrue(buffer.sizeBytes() <= 250)
        assertEquals(2, buffer.snapshot().samples.size)
    }

    @Test
    fun calculatesDurationFromRetainedSamples() {
        val buffer = EncodedRingBuffer(maxDurationUs = 10_000_000, maxBytes = 10_000)

        buffer.append(sample(10_000, 100))
        buffer.append(sample(40_000, 100))

        assertEquals(30_000, buffer.durationUs())
    }

    private fun sample(ptsUs: Long, bytes: Int) = EncodedSample(
        data = ByteArray(bytes),
        presentationTimeUs = ptsUs,
        flags = 0,
    )
}
