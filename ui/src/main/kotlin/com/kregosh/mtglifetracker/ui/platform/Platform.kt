package com.kregosh.mtglifetracker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter

/** The built-in images, by the name both apps store and look them up under. */
enum class BuiltInImage(val fileName: String) {
    ARCANE_STORM("bg_arcane_storm"),
    MANA_ORBS("bg_mana_orbs"),
    PARCHMENT_A("card_parchment_a"),
    PARCHMENT_B("card_parchment_b"),
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

    /** A card texture, drawn while the player hasn't chosen their own card image. */
    @Composable
    fun parchment(image: BuiltInImage): Painter

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
