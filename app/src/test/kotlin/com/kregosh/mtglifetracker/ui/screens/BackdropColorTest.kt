package com.kregosh.mtglifetracker.ui.screens

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class BackdropColorTest {

    private val muted = Color(0xFF49454F)

    @Test
    fun `over a dark background image the text is white, in either theme`() {
        assertEquals(Color.White, onBackdropColor(backdropLuminance = 0.02f, lightTheme = true,  themeColor = muted))
        assertEquals(Color.White, onBackdropColor(backdropLuminance = 0.02f, lightTheme = false, themeColor = muted))
    }

    @Test
    fun `over a bright background image the text is black, in either theme`() {
        assertEquals(Color.Black, onBackdropColor(backdropLuminance = 0.7f, lightTheme = true,  themeColor = muted))
        assertEquals(Color.Black, onBackdropColor(backdropLuminance = 0.7f, lightTheme = false, themeColor = muted))
    }

    @Test
    fun `without a background it is black in light mode and the theme colour in dark mode`() {
        assertEquals(Color.Black, onBackdropColor(backdropLuminance = null, lightTheme = true,  themeColor = muted))
        assertEquals(muted,       onBackdropColor(backdropLuminance = null, lightTheme = false, themeColor = muted))
    }
}
