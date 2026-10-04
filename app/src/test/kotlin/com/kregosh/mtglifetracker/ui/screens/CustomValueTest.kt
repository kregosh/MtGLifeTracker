package com.kregosh.mtglifetracker.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomValueTest {

    @Test
    fun `a number in range is accepted`() {
        assertEquals(25u, parseInRange("25", START_LIFE_RANGE))
        assertEquals(1000u, parseInRange(" 1000 ", START_LIFE_RANGE))
        assertEquals(16u, parseInRange("16", DAMAGE_LIMIT_RANGE))
    }

    @Test
    fun `anything the database would refuse is not`() {
        assertNull(parseInRange("", START_LIFE_RANGE))
        assertNull(parseInRange("0", START_LIFE_RANGE))
        assertNull(parseInRange("1001", START_LIFE_RANGE))
        assertNull(parseInRange("101", DAMAGE_LIMIT_RANGE))
        assertNull(parseInRange("-5", DAMAGE_LIMIT_RANGE))
        assertNull(parseInRange("2x", DAMAGE_LIMIT_RANGE))
    }

    @Test
    fun `the ranges match database rules json`() {
        assertEquals(1u..1000u, START_LIFE_RANGE)
        assertEquals(1u..100u, DAMAGE_LIMIT_RANGE)
    }
}
