package com.kregosh.mtglifetracker.ui.components

import com.kregosh.mtglifetracker.ui.Strings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.kregosh.mtglifetracker.ui.platform.BuiltInImage
import com.kregosh.mtglifetracker.ui.platform.LocalPlatform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.ui.theme.LocalCardBackground
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.isDead
import kotlin.random.Random

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

// Life-total inks
private val INK_IRON_GALL = Color(0xFF15254A) // dark bluish iron-gall ink
private val INK_BLOOD     = Color(0xFF6E0C0C) // dried-blood red for a dead player

private val BANDEROLE_WIDTH     = 70.dp
private val BANDEROLE_HEIGHT    = 42.dp
private val BANDEROLE_CLEARANCE = 34.dp

// Corner banderole: solid single-MTG-color triangular sash in the player's assigned color.

@Composable
private fun ManaBanderole(accentIndex: Int) {
    val color = MANA_COLORS[accentIndex]
    Canvas(Modifier.size(BANDEROLE_WIDTH, BANDEROLE_HEIGHT)) {
        val w = size.width
        val h = size.height
        val inner = 0.36f

        // Drop shadow along the outer edge
        drawLine(
            color       = Color.Black.copy(alpha = 0.28f),
            start       = Offset(w + 2.dp.toPx(), 0f),
            end         = Offset(0f, h + 2.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
        )

        // Single solid triangle from the inner edge to the outer corner
        val sash = Path().apply {
            moveTo(w * inner, 0f)
            lineTo(w, 0f)
            lineTo(0f, h)
            lineTo(0f, h * inner)
            close()
        }
        drawPath(sash, color.copy(alpha = 0.93f))

        val edge = PARCHMENT_INK.copy(alpha = 0.7f)
        drawLine(edge, Offset(w * inner, 0f), Offset(0f, h * inner), strokeWidth = 1.dp.toPx())
        drawLine(edge, Offset(w, 0f), Offset(0f, h), strokeWidth = 1.2.dp.toPx())
    }
}

// Ink on paper: bleed halo + jittered frayed edges + wet core + dry-brush dropouts.
// Randomness is seeded by the text so the texture doesn't flicker on recomposition.

@Composable
private fun InkedNumber(
    text    : String,
    ink     : Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bleedBlur = with(density) { 5.dp.toPx() }
    val wetBlur   = with(density) { 1.dp.toPx() }

    val fray = remember(text) {
        val rnd = Random(text.hashCode())
        List(8) { Offset(rnd.nextFloat() * 2f - 1f, rnd.nextFloat() * 2f - 1f) }
    }
    val dropouts = remember(text) {
        val rnd = Random(text.hashCode() * 31 + 7)
        List(55) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }

    val base = TextStyle(
        fontSize   = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Serif,
        textAlign  = TextAlign.Center,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                dropouts.forEach { (fx, fy, fr) ->
                    drawCircle(
                        color     = Color.Black.copy(alpha = 0.35f + fr * 0.5f),
                        radius    = (0.35f + fr * 1.1f).dp.toPx(),
                        center    = Offset(fx * size.width, fy * size.height),
                        blendMode = BlendMode.DstOut,
                    )
                }
            },
    ) {
        Text(
            text  = text,
            style = base.copy(
                color  = ink.copy(alpha = 0.22f),
                shadow = Shadow(ink.copy(alpha = 0.6f), Offset.Zero, bleedBlur),
            ),
        )
        fray.forEach { o ->
            Text(
                text     = text,
                style    = base.copy(color = ink.copy(alpha = 0.2f)),
                modifier = Modifier.offset((o.x * 1.4f).dp, (o.y * 1.4f).dp),
            )
        }
        Text(
            text  = text,
            style = base.copy(
                color  = ink,
                shadow = Shadow(ink.copy(alpha = 0.85f), Offset.Zero, wetBlur),
            ),
        )
    }
}

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
    onRemove    : (() -> Unit)?  = null,
    onTakeMonarch : (() -> Unit)? = null,
    playerIndex : Int            = 0,
    modifier    : Modifier       = Modifier,
) {
    val dead      = user.isDead(sessionUi)
    val conceded  = user.conceded
    val cardBg    = LocalCardBackground.current
    val hasCardBg = isMe && cardBg != null

    // Alternate between two parchment textures so adjacent cards feel distinct
    val parchment = if (playerIndex % 2 == 0) BuiltInImage.PARCHMENT_A else BuiltInImage.PARCHMENT_B

    Box(modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (user.online) 1f else 0.6f }) {
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
                    // SrcAtop darkens only the page itself, so its transparent torn margins stay clear
                    Image(
                        painter            = LocalPlatform.current.parchment(parchment),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.matchParentSize(),
                        colorFilter        = ColorFilter.tint(Color.Black.copy(alpha = 0.22f), BlendMode.SrcAtop),
                    )
                }

                ManaBanderole(accentIndex = playerIndex % MANA_COLORS.size)

                // ── Content ───────────────────────────────────────────────
                Column(
                    modifier = Modifier.padding(
                        start  = 16.dp,
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
                        Spacer(Modifier.width(BANDEROLE_CLEARANCE))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = user.displayName,
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                                color      = PARCHMENT_INK,
                            )
                            if (!user.online) {
                                Text(
                                    text  = Strings.playerOffline,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PARCHMENT_SEPIA,
                                )
                            }
                        }
                        if (onRemove != null) {
                            IconButton(
                                onClick  = onRemove,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.PersonRemove,
                                    contentDescription = Strings.playerRemove(user.displayName),
                                    modifier           = Modifier.size(18.dp),
                                    tint               = PARCHMENT_SEPIA.copy(alpha = 0.75f),
                                )
                            }
                        }
                        if (!isMe && !isFriend && onAddFriend != null) {
                            IconButton(
                                onClick  = onAddFriend,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.PersonAdd,
                                    contentDescription = Strings.playerSendFriendRequest,
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
                                Text(Strings.playerUndoConcede, style = MaterialTheme.typography.labelSmall, color = PARCHMENT_SEPIA)
                            }
                        } else if (isMe && !dead) {
                            TextButton(
                                onClick        = { onConcede?.invoke() },
                                modifier       = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    Strings.playerConcede,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PARCHMENT_ERROR,
                                )
                            }
                        } else if (isMe) {
                            SuggestionChip(
                                onClick  = {},
                                label    = { Text(Strings.playerYou, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        if (isMe) {
                            SmallAdjustButton(
                                icon        = Icons.Default.Remove,
                                description = Strings.lifeDecrease,
                                enabled     = user.life > 0u,
                                onClick     = { onAdjust(LIFE_STAT, -1) },
                            )
                        }
                        InkedNumber(
                            text     = user.life.toString(),
                            ink      = if (dead) INK_BLOOD else INK_IRON_GALL,
                            fontSize = 60.sp,
                            modifier = Modifier.widthIn(min = 96.dp),
                        )
                        if (isMe) {
                            SmallAdjustButton(
                                icon        = Icons.Default.Add,
                                description = Strings.lifeIncrease,
                                onClick     = { onAdjust(LIFE_STAT, 1) },
                            )
                        }
                    }

                    // The monarch shows on the holder's card; while it's in play, your own card can take it.
                    val isMonarch   = sessionUi.monarch == user.id
                    val showMonarch = isMonarch || (isMe && sessionUi.monarch != null)

                    // The counters this player tracks
                    if (user.stats.isNotEmpty() || showMonarch) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = PARCHMENT_SEPIA.copy(alpha = 0.35f))
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (showMonarch) {
                                MonarchRow(
                                    isMonarch = isMonarch,
                                    isMe      = isMe,
                                    onTake    = { onTakeMonarch?.invoke() },
                                )
                            }
                            user.stats.forEach { (name, type) ->
                                val value = user.customStats[name] ?: 0u
                                val lethalAt = when (name) {
                                    COMMANDER_STAT -> sessionUi.settings.commanderDeathThreshold
                                    POISON_STAT    -> sessionUi.settings.infectDeathThreshold
                                    else           -> null
                                }
                                when (type) {
                                    StatType.NUMERIC -> NumericStatRow(
                                        label    = statShortLabel(name),
                                        value    = value,
                                        isMe     = isMe,
                                        isDead   = lethalAt != null && value >= lethalAt,
                                        onAdjust = { d -> onAdjust(name, d) },
                                    )
                                    StatType.TOGGLE -> ToggleStatRow(
                                        label    = statShortLabel(name),
                                        value    = value,
                                        isMe     = isMe,
                                        onToggle = { onAdjust(name, if (value > 0u) -1 else 1) },
                                    )
                                    StatType.RING_STAGE -> RingStageRow(
                                        label    = statShortLabel(name),
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

        // Overlay for eliminated / conceded players. Lighter on your own card, and it doesn't
        // take touches, so "Undo concede" and the life buttons underneath still work.
        if (dead || conceded) {
            val overlayAlpha = when {
                isMe -> 0.30f
                dead -> 0.60f
                else -> 0.45f
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = overlayAlpha)),
                // On your own card the mark sits in the corner, clear of your life total.
                contentAlignment = if (isMe) Alignment.BottomEnd else Alignment.Center,
            ) {
                Text(
                    text     = if (dead) "💀" else "🏳️",
                    fontSize = if (isMe) 40.sp else 64.sp,
                    modifier = if (isMe) Modifier.padding(12.dp) else Modifier,
                )
            }
        }
    }
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
                    description = Strings.statDecrease(label),
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
                    description = Strings.statIncrease(label),
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
                label    = { Text((if (active) Strings.statActive else Strings.statInactive), style = MaterialTheme.typography.labelSmall) },
            )
        } else {
            AssistChip(
                onClick  = {},
                enabled  = active,
                label    = { Text((if (active) Strings.statActive else Strings.statInactiveOther), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Monarch row: a crown on the holder's card, and a way to take it on your own
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonarchRow(isMonarch: Boolean, isMe: Boolean, onTake: () -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = "👑 " + Strings.playerMonarch,
            style    = MaterialTheme.typography.labelMedium,
            color    = if (isMonarch) PARCHMENT_INK else PARCHMENT_SEPIA,
            fontWeight = if (isMonarch) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        if (isMe && !isMonarch) {
            FilterChip(
                selected = false,
                onClick  = onTake,
                label    = { Text(Strings.playerTakeMonarch, style = MaterialTheme.typography.labelSmall) },
            )
        } else if (isMe) {
            FilterChip(
                selected = true,
                onClick  = {},
                label    = { Text(Strings.statActive, style = MaterialTheme.typography.labelSmall) },
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
                    description = Strings.ringPrevious,
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
                    description = Strings.ringNext,
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
