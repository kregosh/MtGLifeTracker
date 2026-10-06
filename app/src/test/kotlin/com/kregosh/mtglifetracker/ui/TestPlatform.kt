package com.kregosh.mtglifetracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.kregosh.mtglifetracker.ui.platform.BuiltInImage
import com.kregosh.mtglifetracker.ui.platform.LocalPlatform
import com.kregosh.mtglifetracker.ui.platform.Platform

/** A [Platform] for UI tests: no images, sharing or QR codes. */
object TestPlatform : Platform {
    override fun builtInImageUri(image: BuiltInImage) = "test:${image.fileName}"
    override suspend fun loadImage(uri: String, dark: Boolean): ImageBitmap? = null
    @Composable override fun rememberImagePicker(onPicked: (uri: String) -> Unit): () -> Unit = {}
    override fun share(text: String, title: String) {}
    override fun qrModules(content: String): List<BooleanArray> = emptyList()
    @Composable override fun KeepScreenOn() {}
    @Composable override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {}
}

/** Sets [content] with the [TestPlatform] provided, as the app does. */
fun ComposeContentTestRule.setPlatformContent(content: @Composable () -> Unit) = setContent {
    CompositionLocalProvider(LocalPlatform provides TestPlatform, content = content)
}
