package com.kregosh.mtglifetracker.ui.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kregosh.mtglifetracker.shared.inviteUrl

/** Session code, a QR code to scan and a share button with a link chat apps can open. */
@Composable
fun InviteDialog(code: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link    = inviteUrl(code)
    val qr      = remember(link) { qrCode(link) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite players") },
        text  = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.fillMaxWidth(),
            ) {
                Image(
                    bitmap             = qr,
                    contentDescription = "QR code that opens this session",
                    filterQuality      = FilterQuality.None,
                    modifier           = Modifier
                        .size(220.dp)
                        .background(Color.White),
                )
                Text("Scan with the phone camera, or enter the code:")
                Text(
                    code,
                    style      = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val text = "Join my MtG Life Tracker game: $link\nSession code: $code"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "Share invite"))
            }) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Share link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun qrCode(content: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
