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

    @Test
    fun `stat keys route to life, per-opponent commander damage or custom counters`() {
        assertEquals(StatTarget.Life, statTarget(LIFE_STAT))
        assertEquals(StatTarget.CommanderDamage("bob"), statTarget(commanderDamageStat("bob")))
        assertEquals(StatTarget.Custom("poison"), statTarget("poison"))
        assertEquals(StatTarget.Custom(COMMANDER_STAT), statTarget(COMMANDER_STAT))
    }

    @Test
    fun `counters never go below zero`() {
        assertEquals(5L, applyDelta(3L, 2))
        assertEquals(0L, applyDelta(3L, -5))
        assertEquals(2L, applyDelta(null, 2))
        assertEquals(0L, applyDelta(null, -1))
    }
}
