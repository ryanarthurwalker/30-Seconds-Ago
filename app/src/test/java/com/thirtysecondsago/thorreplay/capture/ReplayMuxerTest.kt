package com.thirtysecondsago.thorreplay.capture

import android.media.MediaCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayMuxerTest {
    @Test
    fun selectsPreviousPlayableKeyframeSoReplayIsNotShort() {
        val snapshot = EncodedBufferSnapshot(
            samples = listOf(
                sample(0, keyframe = true),
                sample(1_000_000, keyframe = false),
                sample(2_000_000, keyframe = true),
                sample(3_000_000, keyframe = false),
            ),
            approximateSizeBytes = 4,
        )

        val selected = ReplayMuxer.selectSamplesForReplay(snapshot, requestedDurationUs = 1_500_000)

        assertEquals(0, selected.first().presentationTimeUs)
        assertEquals(4, selected.size)
    }

    @Test
    fun fallsBackToNextKeyframeWhenBufferDoesNotReachRequestedDuration() {
        val snapshot = EncodedBufferSnapshot(
            samples = listOf(
                sample(0, keyframe = false),
                sample(1_000_000, keyframe = true),
                sample(2_000_000, keyframe = false),
            ),
            approximateSizeBytes = 3,
        )

        val selected = ReplayMuxer.selectSamplesForReplay(snapshot, requestedDurationUs = 5_000_000)

        assertEquals(0, selected.first().presentationTimeUs)
        assertEquals(2, selected.size)
    }

    @Test
    fun returnsEmptyWhenNoKeyframeExists() {
        val snapshot = EncodedBufferSnapshot(
            samples = listOf(sample(0, keyframe = false)),
            approximateSizeBytes = 1,
        )

        assertTrue(ReplayMuxer.selectSamplesForReplay(snapshot, 1_000_000).isEmpty())
    }

    @Test
    fun normalizesNonMonotonicTimestamps() {
        val normalized = ReplayMuxer.normalizeTimestamps(
            listOf(
                sample(100, keyframe = true),
                sample(90, keyframe = false),
                sample(120, keyframe = false),
            )
        )

        assertEquals(listOf(0L, 1L, 20L), normalized.map { it.presentationTimeUs })
    }

    private fun sample(ptsUs: Long, keyframe: Boolean) = EncodedSample(
        data = byteArrayOf(1),
        presentationTimeUs = ptsUs,
        flags = if (keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
    )
}
