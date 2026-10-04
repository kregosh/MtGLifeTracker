package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** Quiet zone around the code, in modules. */
private const val QR_MARGIN = 1

/** ARGB pixels (row by row) of [modules] drawn [size]×[size], with a white margin. */
internal fun qrPixels(modules: List<BooleanArray>, size: Int): IntArray {
    val count = modules.size + 2 * QR_MARGIN
    return IntArray(size * size) { i ->
        val row = (i / size) * count / size - QR_MARGIN
        val col = (i % size) * count / size - QR_MARGIN
        if (modules.getOrNull(row)?.getOrNull(col) == true) BLACK else WHITE
    }
}

/** A QR code from its [modules] (true = dark), with a white margin. */
@Composable
internal fun QrCode(modules: List<BooleanArray>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color.White)
        val count  = modules.size + 2 * QR_MARGIN
        val module = minOf(size.width, size.height) / count
        modules.forEachIndexed { row, cells ->
            cells.forEachIndexed { col, dark ->
                if (dark) drawRect(
                    Color.Black,
                    topLeft = Offset((col + QR_MARGIN) * module, (row + QR_MARGIN) * module),
                    // A hair of overlap so neighbouring modules don't show seams.
                    size    = Size(module + 0.5f, module + 0.5f),
                )
            }
        }
    }
}
