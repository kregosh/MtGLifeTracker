package com.kregosh.mtglifetracker.web.platform

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kregosh.mtglifetracker.ui.platform.BuiltInImage
import com.kregosh.mtglifetracker.ui.platform.Platform
import com.kregosh.mtglifetracker.web.firebase.js.callFunction
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val BUILT_IN = "builtin:"

/** Picked images are scaled down to this, so they fit in localStorage. */
private const val PICKED_IMAGE_MAX_SIZE = 1600

/** The shared UI's view of the browser. Picked images are stored as data URLs in the prefs. */
class WebPlatform : Platform {

    override fun builtInImageUri(image: BuiltInImage) = BUILT_IN + image.fileName

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun loadImage(uri: String, dark: Boolean): ImageBitmap? = runCatching {
        val base64 = when {
            uri.startsWith(BUILT_IN) -> {
                val name = uri.removePrefix(BUILT_IN)
                // Only the backgrounds have a night variant.
                val night = dark && name.startsWith("bg_")
                fetchBase64(if (night) "images/night/$name.webp" else "images/$name.webp").await<JsString>().toString()
            }
            uri.startsWith("data:") -> uri.substringAfter(',')
            else -> return null
        }
        org.jetbrains.skia.Image.makeFromEncoded(Base64.decode(base64)).toComposeImageBitmap()
    }.getOrNull()

    // Loaded once and shared by every card.
    private val parchments = mutableStateMapOf<BuiltInImage, ImageBitmap>()

    @Composable
    override fun parchment(image: BuiltInImage): Painter {
        LaunchedEffect(image) {
            if (image !in parchments) loadImage(builtInImageUri(image), dark = false)?.let { parchments[image] = it }
        }
        return parchments[image]?.let { remember(it) { BitmapPainter(it) } } ?: ColorPainter(Color.Transparent)
    }

    @Composable
    override fun rememberImagePicker(onPicked: (uri: String) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onPicked)
        return remember { { pickImage(PICKED_IMAGE_MAX_SIZE) { latest(it) } } }
    }

    override fun share(text: String, title: String) = shareText(text, title)

    override fun qrModules(content: String): List<BooleanArray> {
        val qr = qrcode(0, "L")
        qr.addData(content)
        qr.make()
        val count = qr.getModuleCount()
        return List(count) { row -> BooleanArray(count) { col -> qr.isDark(row, col) } }
    }

    @Composable
    override fun KeepScreenOn() {
        DisposableEffect(Unit) {
            val release = keepScreenOn()
            onDispose { callFunction(release) }
        }
    }

    // Browsers have no back gesture to intercept; the app's own back buttons do the job.
    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {}
}
