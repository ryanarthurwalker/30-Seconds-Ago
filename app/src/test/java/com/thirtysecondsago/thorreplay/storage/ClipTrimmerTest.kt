package com.thirtysecondsago.thorreplay.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipTrimmerTest {
    @Test
    fun clampsTrimRangeToClipDuration() {
        assertEquals(TrimmedRange(9_500L, 10_000L), ClipTrimmer.normalizedRange(12_000L, 15_000L, 10_000L))
    }

    @Test
    fun preservesValidTrimRange() {
        assertEquals(TrimmedRange(2_000L, 8_000L), ClipTrimmer.normalizedRange(2_000L, 8_000L, 10_000L))
    }
}
