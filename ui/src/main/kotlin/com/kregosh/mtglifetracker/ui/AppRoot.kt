package com.kregosh.mtglifetracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.ui.components.LightningOverlay
import com.kregosh.mtglifetracker.ui.components.ManaSparkOverlay
import com.kregosh.mtglifetracker.ui.platform.LocalPlatform
import com.kregosh.mtglifetracker.ui.platform.Platform
import com.kregosh.mtglifetracker.ui.screens.HomeScreen
import com.kregosh.mtglifetracker.ui.screens.SessionScreen
import com.kregosh.mtglifetracker.ui.screens.SettingsScreen
import com.kregosh.mtglifetracker.ui.theme.LocalBackdropLuminance
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.ui.theme.MtGLifeTrackerTheme
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The whole app: theme, background and its effects, and the current screen. */
@Composable
fun AppRoot(vm: SessionViewModel, platform: Platform) {
    val screen          by vm.screen.collectAsState()
    val bgUri           by vm.backgroundImageUri.collectAsState()
    val isStormPreset   by vm.isStormPreset.collectAsState()
    val isManaOrbs      by vm.isManaOrbsPreset.collectAsState()
    val colorScheme     by vm.colorScheme.collectAsState()

    // Built-in backgrounds come in a light and a dark variant, picked by the app's theme.
    val dark = when (colorScheme) {
        AppColorScheme.DARK   -> true
        AppColorScheme.LIGHT  -> false
        AppColorScheme.SYSTEM -> isSystemInDarkTheme()
    }

    var bgBitmap     by remember { mutableStateOf<ImageBitmap?>(null) }
    var bgLuminance  by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(bgUri, dark) {
        bgBitmap    = bgUri?.takeIf { it.isNotEmpty() }?.let { platform.loadImage(it, dark) }
        bgLuminance = bgBitmap?.let { withContext(Dispatchers.Default) { topBandLuminance(it) } }
    }

    CompositionLocalProvider(LocalPlatform provides platform) {
        MtGLifeTrackerTheme(colorScheme = colorScheme) {
            CompositionLocalProvider(
                LocalHasBackground     provides (bgBitmap != null),
                LocalBackdropLuminance provides bgLuminance,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    bgBitmap?.let { bmp ->
                        Image(
                            bitmap             = bmp,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                    }

                    if (isStormPreset) LightningOverlay()

                    val orbsBitmap = bgBitmap
                    if (isManaOrbs && orbsBitmap != null) {
                        ManaSparkOverlay(imageWidth = orbsBitmap.width, imageHeight = orbsBitmap.height, dark = dark)
                    }

                    when (screen) {
                        is Screen.Home     -> HomeScreen(vm)
                        is Screen.Session  -> SessionScreen(vm)
                        is Screen.Settings -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}

/** Average luminance of the image's top band, from a grid of sampled pixels. */
internal fun topBandLuminance(image: ImageBitmap): Float {
    val band    = (image.height * BACKDROP_TEXT_BAND).toInt().coerceIn(1, image.height)
    val rows    = minOf(band, 48)
    val columns = minOf(image.width, 64)
    val row     = IntArray(image.width)
    val samples = IntArray(rows * columns)
    for (r in 0 until rows) {
        image.readPixels(row, startX = 0, startY = r * band / rows, width = image.width, height = 1)
        for (c in 0 until columns) samples[r * columns + c] = row[c * image.width / columns]
    }
    return averageLuminance(samples)
}
