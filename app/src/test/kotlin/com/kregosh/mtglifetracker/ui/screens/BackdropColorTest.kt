package com.kregosh.mtglifetracker.ui.screens

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropColorTest {

    private val muted = Color(0xFF49454F)

    @Test
    fun `over a background image the timer is white, in either theme`() {
        assertEquals(Color.White, onBackdropColor(hasBackground = true, lightTheme = true,  themeColor = muted))
        assertEquals(Color.White, onBackdropColor(hasBackground = true, lightTheme = false, themeColor = muted))
    }

    @Test
    fun `without a background it is black in light mode and the theme colour in dark mode`() {
        assertEquals(Color.Black, onBackdropColor(hasBackground = false, lightTheme = true,  themeColor = muted))
        assertEquals(muted,       onBackdropColor(hasBackground = false, lightTheme = false, themeColor = muted))
    }
}
