package com.kregosh.mtglifetracker.web.platform

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The browser has no emoji font of its own to fall back on, so the few emoji the app shows
 * (skull, flag, crown, lightning, crystal ball) come from a tiny subset of Noto Color Emoji.
 * [content] waits for it, so they never flash up as boxes.
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
fun WithEmojiFont(content: @Composable () -> Unit) {
    val resolver = LocalFontFamilyResolver.current
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching {
            val bytes = Base64.decode(fetchBase64("fonts/NotoColorEmoji-subset.ttf").await<JsString>().toString())
            resolver.preload(FontFamily(Font("NotoColorEmoji", bytes)))
        }
        ready = true
    }
    if (ready) content()
}
