package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.StatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatLabelsTest {

    @Test
    fun `every preset has a distinct name in the stat picker`() {
        val labels = PredefinedStat.entries.map { it.label() }
        labels.forEach { assertTrue(it.isNotBlank()) }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `every preset has a short name for player cards`() {
        PredefinedStat.entries.forEach { stat ->
            val short = stat.shortLabel()
            assertTrue(short.isNotBlank(), stat.name)
            assertTrue(short.length <= stat.label().length, stat.name)
        }
        assertEquals("CMD Dmg", PredefinedStat.COMMANDER.shortLabel())
    }

    @Test
    fun `every stat type has a description`() {
        val labels = StatType.entries.map { it.label() }
        assertEquals(listOf("Counter", "Toggle", "Stage tracker (1–4)"), labels)
    }
}
