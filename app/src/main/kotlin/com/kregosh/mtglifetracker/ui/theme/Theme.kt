package com.kregosh.mtglifetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** True when a user-chosen background image is visible behind the app. */
val LocalHasBackground = compositionLocalOf { false }

private val DarkColors = darkColorScheme()

@Composable
fun MtGLifeTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content     = content,
    )
}
