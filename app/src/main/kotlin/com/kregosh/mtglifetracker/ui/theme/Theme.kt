package com.kregosh.mtglifetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** True when a user-chosen background image is visible behind the app. */
val LocalHasBackground  = compositionLocalOf { false }

/** Non-null when the local player has chosen a card background image. */
val LocalCardBackground = compositionLocalOf<ImageBitmap?> { null }

enum class AppColorScheme { DARK, LIGHT, SYSTEM }

@Composable
fun MtGLifeTrackerTheme(
    colorScheme: AppColorScheme = AppColorScheme.DARK,
    content: @Composable () -> Unit,
) {
    val colors = when (colorScheme) {
        AppColorScheme.DARK   -> darkColorScheme()
        AppColorScheme.LIGHT  -> lightColorScheme()
        AppColorScheme.SYSTEM -> if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
