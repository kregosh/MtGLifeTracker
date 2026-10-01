package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.R
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.ui.theme.LocalCardBackground
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.isDead

// MTG mana color accent bands — assigned to players by index (mod 5)
private val MANA_COLORS = listOf(
    Color(0xFFD4AF37), // White → old gold (parchment is already cream; gold reads as "white mana")
    Color(0xFF1763C6), // Blue
    Color(0xFF3D1055), // Black → deep violet
    Color(0xFFC4391F), // Red
    Color(0xFF1A6B2E), // Green
)

// Ink palette for text rendered on parchment
private val PARCHMENT_INK     = Color(0xFF1E0A00) // very dark brown — names, numbers
private val PARCHMENT_LIGHT   = Color(0xFFEDD9A0) // warm cream      — etched highlight layer
private val PARCHMENT_SEPIA   = Color(0xFF5A3010) // mid sepia       — secondary labels
private val PARCHMENT_ERROR   = Color(0xFF9B1010) // deep red        — dead life total

@Composable
fun PlayerCard(
    user        : UserState,
    isMe        : Boolean,
    sessionUi   : SessionUiState,
    onAdjust    : (stat: String, delta: Int) -> Unit = { _, _ -> },
    onConcede   : (() -> Unit)?  = null,
    onUnconcede : (() -> Unit)?  = null,
    isFriend    : Boolean        = false,
    onAddFriend : (() -> Unit)?  = null,
    playerIndex : Int            = 0,
    modifier    : Modifier       = Modifier,
) {
    val dead      = user.isDead(sessionUi)
    val conceded  = user.conceded
    val cardBg    = LocalCardBackground.current
    val hasCardBg = isMe && cardBg != null
    val manaColor = MANA_COLORS[playerIndex % MANA_COLORS.size]

    // Alternate between two parchment textures so adjacent cards feel distinct
    val parchmentRes = if (playerIndex % 2 == 0) R.drawable.card_parchment_a else R.drawable.card_parchment_b

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier  = Modifier.fillMaxWidth(),
        ) {
            Box {
                // ── Card surface: custom image or default parchment ───────
                if (hasCardBg) {
                    Image(
                        bitmap             = cardBg!!,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.matchParentSize(),
                        alpha              = 0.90f,
                    )
                } else {
                    Image(
                        painter            = painterResource(parchmentRes),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.matchParentSize(),
                    )
                    // Darken the parchment so text reads cleanly
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.22f))
                    )
                }

                // ── Asymmetric mana-color left band ───────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(14.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(manaColor.copy(alpha = 0.88f), Color.Transparent),
                            )
                        )
                )

                // ── Content ───────────────────────────────────────────────
                Column(
                    modifier = Modifier.padding(
                        start  = 20.dp,
                        end    = 12.dp,
                        top    = 10.dp,
                        bottom = 12.dp,
                    )
                ) {

                    // Header row: name + action button
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text       = user.displayName,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                            color      = PARCHMENT_INK,
                            modifier   = Modifier.weight(1f),
                        )
                        if (!isMe && !isFriend && onAddFriend != null) {
                            IconButton(
                                onClick  = onAddFriend,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.PersonAdd,
                                    contentDescription = "Send friend request",
                                    modifier           = Modifier.size(18.dp),
                                    tint               = PARCHMENT_SEPIA.copy(alpha = 0.75f),
                                )
                            }
                        } else if (isMe && conceded) {
                            TextButton(
                                onClick        = { onUnconcede?.invoke() },
                                modifier       = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("Undo", style = MaterialTheme.typography.labelSmall, color = PARCHMENT_SEPIA)
                            }
                        } else if (isMe && !dead) {
                            TextButton(
                                onClick        = { onConcede?.invoke() },
                                modifier       = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    "Concede",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PARCHMENT_ERROR,
                                )
                            }
                        } else if (isMe) {
                            SuggestionChip(
                                onClick  = {},
                                label    = { Text("You", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Life total — layered Text to simulate etching into parchment
                    val lifeColor = if (dead) PARCHMENT_ERROR else PARCHMENT_INK
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier.widthIn(min = 80.dp),
                        ) {
                            // Ink-bleed shadow: same dark tone, slightly offset, lower opacity
                            Text(
                                text       = user.life.toString(),
                                fontSize   = 56.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign  = TextAlign.Center,
                                color      = lifeColor.copy(alpha = 0.35f),
                                modifier   = Modifier.offset(1.dp, 1.dp),
                            )
                            // Primary ink layer on top
                            Text(
                                text       = user.life.toString(),
                                fontSize   = 56.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign  = TextAlign.Center,
                                color      = lifeColor,
                            )
                        }
                        if (isMe) {
                            SmallAdjustButton(
                                icon        = Icons.Default.Add,
                                description = "Increase life",
                                onClick     = { onAdjust("life", 1) },
                            )
                        }
                    }

                    // Custom stats
                    if (sessionUi.statDefs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = PARCHMENT_SEPIA.copy(alpha = 0.35f))
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

        // Overlay for eliminated / conceded opponents
        if (!isMe && (dead || conceded)) {
            val overlayAlpha = if (dead) 0.60f else 0.45f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = overlayAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (dead) "💀" else "🏳️", fontSize = 64.sp)
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
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = if (isDead) PARCHMENT_ERROR else PARCHMENT_SEPIA,
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
                color      = if (isDead) PARCHMENT_ERROR else PARCHMENT_INK,
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
    val active = value > 0u
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = PARCHMENT_SEPIA,
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
    value   : UInt,
    isMe    : Boolean,
    onAdjust: (Int) -> Unit,
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelMedium,
            color    = PARCHMENT_SEPIA,
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..4).forEach { stage ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (value >= stage.toUInt()) PARCHMENT_INK
                                else PARCHMENT_SEPIA.copy(alpha = 0.25f)
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
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (enabled) PARCHMENT_INK.copy(alpha = 0.82f)
                else         PARCHMENT_INK.copy(alpha = 0.28f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = description,
            modifier           = Modifier.size(18.dp),
            tint               = if (enabled) PARCHMENT_LIGHT else PARCHMENT_LIGHT.copy(alpha = 0.45f),
        )
    }
}
