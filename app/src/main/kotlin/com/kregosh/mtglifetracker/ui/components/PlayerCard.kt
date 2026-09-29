package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.UserState

/**
 * Displays one player's card.
 *
 * @param user      The player whose state to render.
 * @param isMe      Whether this card belongs to the local user.
 * @param onIncrement Called when the "+" button is tapped.
 * @param onDecrement Called when the "−" button is tapped.
 */
@Composable
fun PlayerCard(
    user       : UserState,
    isMe       : Boolean,
    onIncrement: () -> Unit = {},
    onDecrement: () -> Unit = {},
    modifier   : Modifier = Modifier,
) {
    val containerColor = if (isMe)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors   = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Name + "You" badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                )
                if (isMe) {
                    Text(
                        text  = "You",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Value + controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isMe) {
                    FilledIconButton(
                        onClick  = onDecrement,
                        enabled  = user.value > 0u,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                }

                Text(
                    text     = user.value.toString(),
                    style    = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 64.dp),
                )

                if (isMe) {
                    FilledIconButton(onClick = onIncrement) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }
        }
    }
}
