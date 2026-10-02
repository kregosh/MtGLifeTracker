package com.kregosh.mtglifetracker.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kregosh.mtglifetracker.shared.PredefinedStat
import com.kregosh.mtglifetracker.shared.StatType
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StatLabelsTest {

    private val res = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `every preset has a distinct name in the stat picker`() {
        val labels = PredefinedStat.entries.map { res.getString(it.labelRes()) }
        labels.forEach { assertTrue(it.isNotBlank()) }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `every preset has a short name for player cards`() {
        PredefinedStat.entries.forEach { stat ->
            val short = res.getString(stat.shortLabelRes())
            assertTrue(short.isNotBlank(), stat.name)
            assertTrue(short.length <= res.getString(stat.labelRes()).length, stat.name)
        }
        assertEquals("CMD Dmg", res.getString(PredefinedStat.COMMANDER.shortLabelRes()))
    }

    @Test
    fun `every stat type has a description`() {
        val labels = StatType.entries.map { res.getString(it.labelRes()) }
        assertEquals(listOf("Counter", "Toggle", "Stage tracker (1–4)"), labels)
    }
}
