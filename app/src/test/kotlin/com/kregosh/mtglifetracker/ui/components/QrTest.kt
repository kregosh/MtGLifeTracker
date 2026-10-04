package com.kregosh.mtglifetracker.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.kregosh.mtglifetracker.encodeQr
import com.kregosh.mtglifetracker.shared.inviteUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrTest {

    private fun modules(content: String) = encodeQr(content)

    private fun decode(pixels: IntArray, size: Int): String =
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))).text

    @Test
    fun `the QR code decodes back to the invite link`() {
        val link = inviteUrl("ABCD2345")
        assertEquals(link, decode(qrPixels(modules(link), 256), 256))
    }

    @Test
    fun `the code has the requested size and only black and white pixels`() {
        val pixels = qrPixels(modules(inviteUrl("ABCD2345")), 300)
        assertEquals(300 * 300, pixels.size)
        assertTrue(pixels.all { it == 0xFF000000.toInt() || it == 0xFFFFFFFF.toInt() })
    }
}
