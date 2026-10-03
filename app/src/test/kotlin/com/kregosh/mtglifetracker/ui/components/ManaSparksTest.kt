package com.kregosh.mtglifetracker.ui.components

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManaSparksTest {

    @Test
    fun `pulses have random lengths and strengths within bounds`() {
        val rng    = Random(1)
        val pulses = List(1_000) { nextPulse(rng) }
        assertTrue(pulses.all { it.durationMs in 2_500L..6_000L })
        assertTrue(pulses.all { it.peak in MIN_PULSE_PEAK..MAX_PULSE_PEAK })
        // Genuinely varied, not one fixed rhythm.
        assertTrue(pulses.map { it.durationMs }.toSet().size > 100)
        assertTrue(pulses.maxOf { it.peak } - pulses.minOf { it.peak } > (MAX_PULSE_PEAK - MIN_PULSE_PEAK) * 0.8f)
    }

    @Test
    fun `an orb never stops sparkling`() {
        val pulse = Pulse(durationMs = 4_000L, peak = 10f)
        for (t in listOf(-500f, 0f, 1f, 2_000f, 3_999f, 4_000f, 9_000f)) {
            assertTrue(orbRate(t, pulse) >= BASE_SPARKLES_PER_SECOND, "rate at $t")
        }
    }

    @Test
    fun `a pulse swells to its peak in the middle and fades at both ends`() {
        val pulse = Pulse(durationMs = 4_000L, peak = 10f)
        assertEquals(BASE_SPARKLES_PER_SECOND + 10f, orbRate(2_000f, pulse), 1e-3f)
        assertTrue(orbRate(0f, pulse)     < BASE_SPARKLES_PER_SECOND + 0.2f)
        assertTrue(orbRate(4_000f, pulse) < BASE_SPARKLES_PER_SECOND + 0.2f)
        assertTrue(orbRate(1_000f, pulse) < orbRate(1_500f, pulse))
        assertTrue(orbRate(2_500f, pulse) > orbRate(3_000f, pulse))
        assertEquals(orbRate(1_200f, pulse), orbRate(2_800f, pulse), 1e-4f)
    }

    @Test
    fun `the swell is zero outside its pulse`() {
        assertEquals(0f, swell(-1f, 4_000f, 10f))
        assertEquals(0f, swell(4_001f, 4_000f, 10f))
        assertEquals(0f, swell(10f, 0f, 10f))
    }

    @Test
    fun `a strong pulse gives a stream, not a flood`() {
        // Integrate the strongest 4 s pulse: a few dozen sparkles from one orb, not hundreds.
        val pulse = Pulse(durationMs = 4_000L, peak = MAX_PULSE_PEAK)
        val total = (0..4_000 step 10).sumOf { t -> (orbRate(t.toFloat(), pulse) * 0.01f).toDouble() }
        assertTrue(total in 20.0..35.0, "total $total")
    }

    @Test
    fun `both themes have five orbs, left to right, inside the image`() {
        for (dark in listOf(false, true)) {
            val orbs = manaOrbSpots(dark)
            assertEquals(5, orbs.size)
            assertEquals(orbs.sortedBy { it.x }, orbs)
            assertTrue(orbs.all { it.x in 0f..1f && it.y in 0f..1f })
        }
    }

    @Test
    fun `a taller screen crops the sides and keeps orbs on their spot in the image`() {
        // 688×1529 image on a 1080×2640 screen: scaled by height, sides cropped.
        val mapping = CropMapping(688f, 1529f, 1080f, 2640f)
        val scale   = 2640f / 1529f
        val middle  = OrbSpot(0.5f, 0.5f, 0.1f)
        assertEquals(scale, mapping.scale, 1e-4f)
        assertEquals(540f, mapping.centreX(middle), 0.5f)
        assertEquals(1320f, mapping.centreY(middle), 0.5f)
        assertEquals(68.8f * scale, mapping.radius(middle), 0.5f)

        val left = OrbSpot(0f, 0f, 0.1f)
        assertEquals((1080f - 688f * scale) / 2f, mapping.centreX(left), 0.5f)
        assertEquals(0f, mapping.centreY(left), 0.5f)
    }

    @Test
    fun `a wider screen crops top and bottom`() {
        val mapping = CropMapping(688f, 1529f, 1600f, 2000f)
        val scale   = 1600f / 688f
        assertEquals(scale, mapping.scale, 1e-4f)
        assertEquals(0f, mapping.centreX(OrbSpot(0f, 0.5f, 0.1f)), 0.5f)
        assertEquals(1000f, mapping.centreY(OrbSpot(0f, 0.5f, 0.1f)), 0.5f)
    }
}
