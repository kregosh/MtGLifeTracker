package com.kregosh.mtglifetracker.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** ARGB pixels (row by row) of a [size]×[size] QR code for [content]. */
internal fun qrPixels(content: String, size: Int): IntArray {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1),
    )
    return IntArray(size * size) { i -> if (matrix[i % size, i / size]) BLACK else WHITE }
}

internal fun qrCode(content: String, size: Int = 512): ImageBitmap =
    Bitmap.createBitmap(qrPixels(content, size), size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
