package com.kregosh.mtglifetracker.ui.components

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManaSparksTest {

    @Test
    fun `bursts come every 0_2 to 0_5 seconds`() {
        val rng = Random(1)
        repeat(1_000) { assertTrue(nextBurstDelayMs(rng) in 200L..500L) }
    }

    @Test
    fun `a burst uses one to three different orbs, usually one`() {
        val rng    = Random(2)
        val counts = IntArray(4)
        repeat(1_000) {
            val picked = pickOrbs(rng)
            assertTrue(picked.size in 1..3)
            assertEquals(picked.size, picked.toSet().size)
            assertTrue(picked.all { it in 0..4 })
            counts[picked.size]++
        }
        assertTrue(counts[1] > counts[2] && counts[2] > counts[3] && counts[3] > 0)
    }

    @Test
    fun `every orb gets its turn`() {
        val rng  = Random(3)
        val seen = (1..200).flatMap { pickOrbs(rng) }.toSet()
        assertEquals((0..4).toSet(), seen)
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
