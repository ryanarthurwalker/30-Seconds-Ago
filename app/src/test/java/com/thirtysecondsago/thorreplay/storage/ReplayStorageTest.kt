package com.thirtysecondsago.thorreplay.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReplayStorageTest {
    @Test
    fun generatesExpectedFilename() {
        val filename = ReplayStorage.filenameFor(LocalDateTime.of(2026, 7, 31, 15, 42, 10))

        assertEquals("ThorReplay_2026-07-31_15-42-10.mp4", filename)
    }
}
