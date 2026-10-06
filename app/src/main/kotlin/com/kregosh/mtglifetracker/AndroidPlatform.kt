package com.kregosh.mtglifetracker

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.kregosh.mtglifetracker.ui.platform.BuiltInImage
import com.kregosh.mtglifetracker.ui.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The shared UI's view of Android: resources, the content resolver, intents and the window. */
class AndroidPlatform(private val activity: Activity) : Platform {

    override fun builtInImageUri(image: BuiltInImage) =
        "android.resource://${activity.packageName}/drawable/${image.fileName}"

    override suspend fun loadImage(uri: String, dark: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            // Built-in backgrounds resolve their night variant by the app's theme, not the system's.
            val context = if (uri.startsWith("android.resource://")) {
                val cfg = Configuration(activity.resources.configuration)
                val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                activity.createConfigurationContext(cfg)
            } else activity
            context.contentResolver.openInputStream(Uri.parse(uri))
                ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }

    @Composable
    override fun rememberImagePicker(onPicked: (uri: String) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            // Keep access to the image after a restart.
            runCatching {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPicked(uri.toString())
        }
        return { launcher.launch("image/*") }
    }

    override fun share(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, title))
    }

    override fun qrModules(content: String) = encodeQr(content)

    @Composable
    override fun KeepScreenOn() {
        DisposableEffect(Unit) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        androidx.activity.compose.BackHandler(enabled, onBack)
    }
}

/** QR code modules for [content], row by row (true = dark). */
internal fun encodeQr(content: String): List<BooleanArray> {
    val matrix = Encoder.encode(content, ErrorCorrectionLevel.L).matrix
    return List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y].toInt() == 1 } }
}
