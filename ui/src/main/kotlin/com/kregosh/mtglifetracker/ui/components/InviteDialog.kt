package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.ui.Strings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.ui.platform.LocalPlatform
import com.kregosh.mtglifetracker.shared.inviteUrl

/** Session code, a QR code to scan and a share button with a link chat apps can open. */
@Composable
fun InviteDialog(code: String, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val link    = inviteUrl(code)
    val qr      = remember(link) { platform.qrModules(link) }
    val shareText    = Strings.inviteShareText(link, code)
    val chooserTitle = Strings.inviteShareChooser

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.inviteTitle) },
        text  = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.fillMaxWidth(),
            ) {
                QrCode(
                    modules  = qr,
                    modifier = Modifier
                        .size(220.dp)
                        .semantics { contentDescription = Strings.inviteQrDescription },
                )
                Text(Strings.inviteHint)
                Text(
                    code,
                    style      = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                platform.share(shareText, chooserTitle)
            }) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(Strings.inviteShare)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.actionClose) }
        },
    )
}
