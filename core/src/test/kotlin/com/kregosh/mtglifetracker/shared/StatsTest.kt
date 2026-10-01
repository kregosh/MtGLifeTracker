package com.kregosh.mtglifetracker.shared

import com.kregosh.mtglifetracker.viewmodel.isValidStatName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {

    @Test
    fun `preset ids are unique valid stat names`() {
        val ids = PredefinedStat.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(isValidStatName(it), it) }
    }

    @Test
    fun `presets are found by id`() {
        PredefinedStat.entries.forEach { assertEquals(it, PredefinedStat.fromId(it.id)) }
        assertNull(PredefinedStat.fromId("gold"))
    }

    @Test
    fun `commander damage keys round-trip and never look like custom stats`() {
        val key = commanderDamageStat("opponent-id")
        assertEquals("opponent-id", commanderDamageSource(key))
        assertNull(commanderDamageSource(COMMANDER_STAT))
        assertTrue(!isValidStatName(key))
    }
}
