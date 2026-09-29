package com.kregosh.mtglifetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** True when a user-chosen background image is visible behind the app. */
val LocalHasBackground  = compositionLocalOf { false }

/** Non-null when the local player has chosen a card background image. */
val LocalCardBackground = compositionLocalOf<ImageBitmap?> { null }

enum class AppColorScheme { DARK, LIGHT, SYSTEM }

// ─── Day/Night game-state colour schemes ─────────────────────────────────────

fun dayColorScheme(): ColorScheme = lightColorScheme(
    primary            = Color(0xFFB5690A),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF3B1900),
    secondary            = Color(0xFF6D5E0F),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFF8E287),
    onSecondaryContainer = Color(0xFF221B00),
    tertiary            = Color(0xFF4D6600),
    onTertiary          = Color(0xFFFFFFFF),
    tertiaryContainer   = Color(0xFFCDEF68),
    onTertiaryContainer = Color(0xFF151F00),
    background     = Color(0xFFFFF8F0),
    onBackground   = Color(0xFF201A13),
    surface        = Color(0xFFFFF8F0),
    onSurface      = Color(0xFF201A13),
    surfaceVariant   = Color(0xFFF0E0CF),
    onSurfaceVariant = Color(0xFF504539),
    error   = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

fun nightColorScheme(): ColorScheme = darkColorScheme(
    primary            = Color(0xFF93CCFF),
    onPrimary          = Color(0xFF003352),
    primaryContainer   = Color(0xFF004B75),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary            = Color(0xFFB8C8E8),
    onSecondary          = Color(0xFF223344),
    secondaryContainer   = Color(0xFF38495C),
    onSecondaryContainer = Color(0xFFD4E4F7),
    tertiary            = Color(0xFFD5BBE8),
    onTertiary          = Color(0xFF3B2651),
    tertiaryContainer   = Color(0xFF523D69),
    onTertiaryContainer = Color(0xFFF1D8FF),
    background     = Color(0xFF080D1C),
    onBackground   = Color(0xFFE2E2EC),
    surface        = Color(0xFF080D1C),
    onSurface      = Color(0xFFE2E2EC),
    surfaceVariant   = Color(0xFF18233A),
    onSurfaceVariant = Color(0xFFC0C6D8),
    error   = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

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
