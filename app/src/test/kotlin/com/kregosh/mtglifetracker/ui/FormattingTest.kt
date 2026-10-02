package com.kregosh.mtglifetracker.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormattingTest {

    @Test
    fun `timer shows minutes and seconds under an hour`() {
        assertEquals("00:00", Duration.ZERO.toTimerString())
        assertEquals("00:05", 5.seconds.toTimerString())
        assertEquals("01:05", 65.seconds.toTimerString())
        assertEquals("59:59", (60.minutes - 1.seconds).toTimerString())
    }

    @Test
    fun `timer adds hours from one hour on`() {
        assertEquals("1:00:00", 1.hours.toTimerString())
        assertEquals("2:03:04", (2.hours + 3.minutes + 4.seconds).toTimerString())
    }

    @Test
    fun `timer drops fractions of a second and never goes negative`() {
        assertEquals("00:01", 1999.milliseconds.toTimerString())
        assertEquals("00:00", (-5).seconds.toTimerString())
    }

    @Test
    fun `life deltas carry a plus or a typographic minus`() {
        assertEquals("+3", 3.signed())
        assertEquals("−2", (-2).signed())
        assertEquals("−0", 0.signed())
    }
}
