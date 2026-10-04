package com.kregosh.mtglifetracker.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackdropTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `black and white are the ends of the luminance scale`() {
        assertEquals(0f, relativeLuminance(black), 1e-6f)
        assertEquals(1f, relativeLuminance(white), 1e-4f)
    }

    @Test
    fun `green looks brighter than red, which looks brighter than blue`() {
        val red   = relativeLuminance(0xFFFF0000.toInt())
        val green = relativeLuminance(0xFF00FF00.toInt())
        val blue  = relativeLuminance(0xFF0000FF.toInt())
        assertTrue(green > red && red > blue)
        assertEquals(0.2126f, red, 1e-3f)
    }

    @Test
    fun `mid grey is gamma corrected, not halfway`() {
        assertEquals(0.216f, relativeLuminance(0xFF808080.toInt()), 0.005f)
    }

    @Test
    fun `the average covers every pixel`() {
        assertEquals(0.5f, averageLuminance(intArrayOf(black, white)), 1e-4f)
        assertEquals(0f, averageLuminance(IntArray(0)))
    }

    @Test
    fun `dark backdrops get white text and bright ones black`() {
        assertFalse(prefersDarkText(relativeLuminance(0xFF0B1A2E.toInt())))  // night sky
        assertTrue(prefersDarkText(relativeLuminance(0xFFE8E0D0.toInt())))   // parchment
        assertFalse(prefersDarkText(relativeLuminance(0xFF404040.toInt())))  // dark grey
        assertTrue(prefersDarkText(relativeLuminance(0xFF909090.toInt())))   // light grey
    }
}
