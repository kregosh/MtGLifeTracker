package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.isDead

@Composable
fun PlayerCard(
    user      : UserState,
    isMe      : Boolean,
    sessionUi : SessionUiState,
    onAdjust  : (stat: String, delta: Int) -> Unit = { _, _ -> },
    modifier  : Modifier = Modifier,
) {
    val dead = user.isDead(sessionUi)

    val containerColor = when {
        dead  -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        isMe  -> MaterialTheme.colorScheme.primaryContainer
        else  -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            colors   = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // ── Header: name + badge ──────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text       = user.displayName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                        modifier   = Modifier.weight(1f),
                    )
                    if (isMe) {
                        SuggestionChip(
                            onClick = {},
                            label   = { Text("You", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Life total ────────────────────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    if (isMe) {
                        SmallAdjustButton(
                            icon        = Icons.Default.Remove,
                            description = "Decrease life",
                            enabled     = user.life > 0u,
                            onClick     = { onAdjust("life", -1) },
                        )
                    }

                    Text(
                        text      = user.life.toString(),
                        fontSize  = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.widthIn(min = 80.dp),
                        color      = if (dead) MaterialTheme.colorScheme.onErrorContainer
                                     else     LocalContentColor.current,
                    )

                    if (isMe) {
                        SmallAdjustButton(
                            icon        = Icons.Default.Add,
                            description = "Increase life",
                            onClick     = { onAdjust("life", 1) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── Secondary stats ───────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatRow(
                        label   = "CMD",
                        value   = user.commanderDamage,
                        isMe    = isMe,
                        dead    = user.commanderDamage >= sessionUi.commanderDeathThreshold,
                        onAdjust = { d -> onAdjust("commander", d) },
                    )
                    StatRow(
                        label    = "PSN",
                        value    = user.poisonDamage,
                        isMe     = isMe,
                        dead     = user.poisonDamage >= sessionUi.poisonDeathThreshold,
                        onAdjust = { d -> onAdjust("poison", d) },
                    )
                    user.customStats.forEach { (name, value) ->
                        StatRow(
                            label    = name,
                            value    = value,
                            isMe     = isMe,
                            dead     = false,
                            onAdjust = { d -> onAdjust(name, d) },
                        )
                    }
                }
            }
        }

        // ── Skull overlay — only for others; your own card stays interactive ──
        if (dead && !isMe) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "💀", fontSize = 64.sp)
            }
        }
    }
}

@Composable
private fun StatRow(
    label   : String,
    value   : UInt,
    isMe    : Boolean,
    dead    : Boolean,
    onAdjust: (Int) -> Unit,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (dead) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 56.dp),
        )

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isMe) {
                SmallAdjustButton(
                    icon        = Icons.Default.Remove,
                    description = "Decrease $label",
                    enabled     = value > 0u,
                    onClick     = { onAdjust(-1) },
                )
            }

            Text(
                text       = value.toString(),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.widthIn(min = 32.dp),
                color      = if (dead) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )

            if (isMe) {
                SmallAdjustButton(
                    icon        = Icons.Default.Add,
                    description = "Increase $label",
                    onClick     = { onAdjust(1) },
                )
            }
        }
    }
}

@Composable
private fun SmallAdjustButton(
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled    : Boolean = true,
    onClick    : () -> Unit,
) {
    FilledTonalIconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}
