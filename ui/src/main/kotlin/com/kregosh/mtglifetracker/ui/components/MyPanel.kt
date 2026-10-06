package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.ui.Strings
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.isDead

/** Life, commander damage and poison: the counters with a tab of their own. */
private val FOCUS_TABS = listOf(LIFE_STAT, COMMANDER_STAT, POISON_STAT)

/** How much a long press changes the selected counter. */
private const val LONG_PRESS_STEP = 5

/**
 * Your own status, most of the screen. The big number is the selected counter (life to begin
 * with) and the whole panel is its button: the left half takes one off, the right half adds
 * one, a long press five. The tabs pick life, commander damage or poison; your other counters
 * are small chips, followed by the button that adds one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MyPanel(
    me           : UserState,
    ui           : SessionUiState,
    onAdjust     : (stat: String, delta: Int) -> Unit,
    onTrack      : (stat: String) -> Unit,
    onAddStat    : () -> Unit,
    onTakeMonarch: () -> Unit,
    modifier     : Modifier = Modifier,
) {
    val minorStats = me.stats.filterKeys { it !in FOCUS_TABS }
    var chosen by rememberSaveable { mutableStateOf(LIFE_STAT) }
    // A counter that was turned off since gives the big number back to life.
    val focus = chosen.takeIf { it in FOCUS_TABS || minorStats[it] == StatType.NUMERIC } ?: LIFE_STAT

    fun select(stat: String) {
        // Commander damage and poison always have a tab; using one starts tracking it.
        if (stat != LIFE_STAT && stat in FOCUS_TABS && stat !in me.stats) onTrack(stat)
        chosen = stat
    }

    Column(modifier = modifier.raised(RoundedCornerShape(24.dp))) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Strings.playerYou, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                when {
                    me.isDead(ui) -> Text("  💀", fontSize = 20.sp)
                    me.conceded   -> Text("  🏳️", fontSize = 20.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text  = Strings.focusLabel(focusLabel(focus)),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }

            BigCounter(
                stat     = focus,
                value    = valueOf(me, focus),
                limit    = limitOf(ui, focus),
                onAdjust = { delta -> onAdjust(focus, delta) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FOCUS_TABS.forEach { stat ->
                    FocusTab(
                        stat     = stat,
                        value    = valueOf(me, stat),
                        limit    = limitOf(ui, stat),
                        selected = stat == focus,
                        onClick  = { select(stat) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            FlowRow(
                modifier              = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                ui.monarch?.let { holder ->
                    if (holder == me.id) StatChip("👑 " + Strings.playerMonarch)
                    else StatChip("👑 " + Strings.playerTakeMonarch, onClick = onTakeMonarch)
                }
                minorStats.forEach { (name, type) ->
                    val value = me.customStats[name] ?: 0u
                    val label = statShortLabel(name)
                    when (type) {
                        StatType.NUMERIC    -> StatChip("$label $value", selected = name == focus, onClick = { select(name) })
                        StatType.TOGGLE     -> StatChip("$label: ${statValueText(type, value)}",
                                                   onClick = { onAdjust(name, if (value > 0u) -1 else 1) })
                        // A ring stage counts up to four, then starts over.
                        StatType.RING_STAGE -> StatChip("$label ${statValueText(type, value)}",
                                                   onClick = { onAdjust(name, if (value < 4u) 1 else -value.toInt()) })
                    }
                }
                AddStatButton(compact = minorStats.isNotEmpty(), onClick = onAddStat)
            }
        }
    }
}

private fun focusLabel(stat: String): String = if (stat == LIFE_STAT) Strings.statLife else statShortLabelPlain(stat)

private fun valueOf(me: UserState, stat: String): UInt =
    if (stat == LIFE_STAT) me.life else me.customStats[stat] ?: 0u

/** The value at which [stat] knocks you out, for the counters that have one. */
private fun limitOf(ui: SessionUiState, stat: String): UInt? = when (stat) {
    COMMANDER_STAT -> ui.settings.commanderDeathThreshold
    POISON_STAT    -> ui.settings.infectDeathThreshold
    else           -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigCounter(
    stat    : String,
    value   : UInt,
    limit   : UInt?,
    onAdjust: (Int) -> Unit,
    modifier: Modifier,
) {
    val label = focusLabel(stat)
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val numberSize = (maxHeight.value * 0.55f).coerceIn(48f, 150f)

        if (hasStatIcon(stat)) {
            StatIcon(stat, Modifier.size((numberSize * 1.5f).dp).alpha(0.10f))
        }
        Row(Modifier.fillMaxSize()) {
            TapZone(Strings.statDecrease(label), "−", Alignment.CenterStart, Modifier.weight(1f),
                onTap = { onAdjust(-1) }, onLongPress = { onAdjust(-LONG_PRESS_STEP) })
            Box(
                Modifier.width(1.dp).fillMaxHeight().padding(vertical = 24.dp)
                    .background(LocalContentColor.current.copy(alpha = 0.15f))
            )
            TapZone(Strings.statIncrease(label), "+", Alignment.CenterEnd, Modifier.weight(1f),
                onTap = { onAdjust(1) }, onLongPress = { onAdjust(LONG_PRESS_STEP) })
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.toString(), fontSize = numberSize.sp, fontWeight = FontWeight.Bold, lineHeight = numberSize.sp)
            if (limit != null) {
                Text(
                    text     = "/$limit",
                    fontSize = (numberSize * 0.25f).sp,
                    color    = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = (numberSize * 0.12f).dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TapZone(
    description: String,
    sign       : String,
    alignment  : Alignment,
    modifier   : Modifier,
    onTap      : () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = alignment,
    ) {
        Text(sign, fontSize = 34.sp, color = LocalContentColor.current.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun FocusTab(
    stat    : String,
    value   : UInt,
    limit   : UInt?,
    selected: Boolean,
    onClick : () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val look  = if (selected) Modifier.inset(shape).border(2.dp, LocalContentColor.current, shape)
                else Modifier.raised(shape, elevation = 4.dp)
    Column(
        modifier = modifier
            .height(78.dp)
            .then(look)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = focusLabel(stat)
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatIcon(stat, Modifier.size(26.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (limit != null) {
                Text("/$limit", fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f),
                     modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** A minor counter: pressed into the panel, outlined while it is the big number. */
@Composable
private fun StatChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    var look = Modifier.inset(shape)
    if (selected) look = look.border(2.dp, LocalContentColor.current, shape)
    if (onClick != null) look = look.clickable(onClick = onClick)
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        modifier = look.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** "+ Stat" until you track a minor counter, then just "+" at the end of the chips. */
@Composable
private fun AddStatButton(compact: Boolean, onClick: () -> Unit) {
    Text(
        text     = if (compact) "+" else Strings.addStat,
        style    = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .raised(RoundedCornerShape(12.dp), elevation = 4.dp)
            .semantics { contentDescription = Strings.addStatDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 12.dp, vertical = 8.dp),
    )
}
