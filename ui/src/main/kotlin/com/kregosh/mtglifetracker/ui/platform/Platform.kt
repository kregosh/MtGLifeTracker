package com.kregosh.mtglifetracker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The built-in images, by the name both apps store and look them up under, and whether there
 * is a separate version for the dark theme.
 */
enum class BuiltInImage(val fileName: String, val hasNightVariant: Boolean = true) {
    ARCANE_STORM("bg_arcane_storm"),
    MANA_ORBS("bg_mana_orbs"),
    /** The table view's own background (#117). */
    TABLE("bg_table", hasNightVariant = false),
}

/**
 * What the shared UI needs from the app it runs in. The Android app and the browser app
 * each provide one through [LocalPlatform].
 */
interface Platform {
    /** The stored background value that selects [image], a built-in background. */
    fun builtInImageUri(image: BuiltInImage): String

    /**
     * Loads a background image by its stored value; built-in images come in their
     * [dark] or light variant. Null if it can't be read.
     */
    suspend fun loadImage(uri: String, dark: Boolean): ImageBitmap?

    /**
     * Returns a function that lets the player pick an image from their device; [onPicked]
     * gets a value [loadImage] can read back later.
     */
    @Composable
    fun rememberImagePicker(onPicked: (uri: String) -> Unit): () -> Unit

    /** Offers [text] to other apps (share sheet), or copies it where there's no share sheet. */
    fun share(text: String, title: String)

    /** The modules of a QR code for [content], row by row (true = dark). */
    fun qrModules(content: String): List<BooleanArray>

    /** Keeps the screen on while this is in the composition. */
    @Composable
    fun KeepScreenOn()

    /**
     * Handles the system back gesture while [enabled]; the browser has none. No default for
     * [enabled]: Compose can't call default arguments of an interface's composable.
     */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("No Platform provided") }
