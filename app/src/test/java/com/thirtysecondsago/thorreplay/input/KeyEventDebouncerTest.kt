package com.thirtysecondsago.thorreplay.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEventDebouncerTest {
    @Test
    fun rejectsRepeatedKeyInsideDebounceWindow() {
        val debouncer = KeyEventDebouncer(debounceMs = 1_000)

        assertTrue(debouncer.shouldAccept(keyCode = 100, eventTimeMs = 5_000))
        assertFalse(debouncer.shouldAccept(keyCode = 100, eventTimeMs = 5_500))
    }

    @Test
    fun acceptsSameKeyAfterDebounceWindow() {
        val debouncer = KeyEventDebouncer(debounceMs = 1_000)

        assertTrue(debouncer.shouldAccept(keyCode = 100, eventTimeMs = 5_000))
        assertTrue(debouncer.shouldAccept(keyCode = 100, eventTimeMs = 6_001))
    }

    @Test
    fun acceptsDifferentKeyImmediately() {
        val debouncer = KeyEventDebouncer(debounceMs = 1_000)

        assertTrue(debouncer.shouldAccept(keyCode = 100, eventTimeMs = 5_000))
        assertTrue(debouncer.shouldAccept(keyCode = 101, eventTimeMs = 5_100))
    }
}
