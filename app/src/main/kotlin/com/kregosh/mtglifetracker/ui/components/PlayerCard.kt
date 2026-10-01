package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.ui.theme.LocalCardBackground
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
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
    val dead      = user.isDead(sessionUi)
    val hasBg     = LocalHasBackground.current
    val cardBg    = LocalCardBackground.current
    val hasCardBg = isMe && cardBg != null

    val containerColor = when {
        dead  -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (hasBg || hasCardBg) 0.45f else 0.6f)
        isMe  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (hasCardBg) 0.65f else if (hasBg) 0.72f else 1f)
        else  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (hasBg) 0.60f else 1f)
    }

    val contentColor = if (hasBg || hasCardBg) Color.White else Color.Unspecified

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            colors   = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor   = contentColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                if (hasCardBg) {
                    Image(
                        bitmap             = cardBg!!,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.matchParentSize(),
                        alpha              = 0.55f,
                    )
                }
                Column(modifier = Modifier.padding(12.dp)) {

                    // ── Header ────────────────────────────────────────────
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
                                onClick  = {},
                                label    = { Text("You", style = MaterialTheme.typography.labelSmall) },
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
                            text       = user.life.toString(),
                            fontSize   = 56.sp,
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

                    // ── Optional stats ────────────────────────────────────
                    if (sessionUi.statDefs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            sessionUi.statDefs.forEach { (name, type) ->
                                val value = user.customStats[name] ?: 0u
                                when (type) {
                                    StatType.NUMERIC    -> NumericStatRow(
                                        label    = statLabel(name),
                                        value    = value,
                                        isMe     = isMe,
                                        isDead   = name == "commander" && value >= sessionUi.commanderDeathThreshold
                                                || name == "poison"    && value >= sessionUi.infectDeathThreshold,
                                        onAdjust = { d -> onAdjust(name, d) },
                                    )
                                    StatType.TOGGLE     -> ToggleStatRow(
                                        label    = statLabel(name),
                                        value    = value,
                                        isMe     = isMe,
                                        onToggle = { onAdjust(name, if (value > 0u) -1 else 1) },
                                    )
                                    StatType.RING_STAGE -> RingStageRow(
                                        label    = statLabel(name),
                                        value    = value,
                                        isMe     = isMe,
                                        onAdjust = { d -> onAdjust(name, d) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Skull overlay for eliminated opponents ────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// Stat label helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun statLabel(name: String): String = when (name) {
    "commander"  -> "CMD Dmg"
    "poison"     -> "Poison"
    "energy"     -> "Energy"
    "experience" -> "Exp"
    "storm"      -> "Storm"
    "tax"        -> "CMD Tax"
    "ring"       -> "The Ring"
    "monarch"    -> "Monarch"
    "initiative" -> "Initiative"
    "blessing"   -> "City's Blessing"
    else         -> name
}

// ─────────────────────────────────────────────────────────────────────────────
// Numeric stat row (+/- counter)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NumericStatRow(
    label   : String,
    value   : UInt,
    isMe    : Boolean,
    isDead  : Boolean,
    onAdjust: (Int) -> Unit,
) {
    val labelColor = LocalContentColor.current.copy(alpha = 0.75f)
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = if (isDead) MaterialTheme.colorScheme.error else labelColor,
            modifier = Modifier.widthIn(min = 72.dp),
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
                color      = if (isDead) MaterialTheme.colorScheme.error else LocalContentColor.current,
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

// ─────────────────────────────────────────────────────────────────────────────
// Toggle stat row (on/off badge)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToggleStatRow(
    label   : String,
    value   : UInt,
    isMe    : Boolean,
    onToggle: () -> Unit,
) {
    val active     = value > 0u
    val labelColor = LocalContentColor.current.copy(alpha = 0.75f)
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = labelColor,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        if (isMe) {
            FilterChip(
                selected = active,
                onClick  = onToggle,
                label    = { Text(if (active) "Active" else "Inactive", style = MaterialTheme.typography.labelSmall) },
            )
        } else {
            AssistChip(
                onClick  = {},
                enabled  = active,
                label    = { Text(if (active) "Active" else "—", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ring-stage row (4-pip tracker)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RingStageRow(
    label   : String,
    value   : UInt,   // 0 = not tempted, 1–4 = stage
    isMe    : Boolean,
    onAdjust: (Int) -> Unit,
) {
    val labelColor = LocalContentColor.current.copy(alpha = 0.75f)
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = labelColor,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isMe) {
                SmallAdjustButton(
                    icon        = Icons.Default.Remove,
                    description = "Previous ring stage",
                    enabled     = value > 0u,
                    onClick     = { onAdjust(-1) },
                )
            }
            // 4 pips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..4).forEach { stage ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (value >= stage.toUInt()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
            if (isMe) {
                SmallAdjustButton(
                    icon        = Icons.Default.Add,
                    description = "Next ring stage",
                    enabled     = value < 4u,
                    onClick     = { onAdjust(1) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared small button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SmallAdjustButton(
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
