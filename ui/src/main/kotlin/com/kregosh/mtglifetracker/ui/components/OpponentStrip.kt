package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.StatType
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.ui.Strings
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.isDead

/**
 * The other players, small: life, commander damage and poison only. Up to three a row,
 * the rows sharing the width. Tapping or long-pressing a player shows everything else.
 */
@Composable
internal fun OpponentStrip(
    players : List<UserState>,
    ui      : SessionUiState,
    onOpen  : (UserState) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (players.isEmpty()) return
    val rows = (players.size + 2) / 3
    val perRow = (players.size + rows - 1) / rows
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        players.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { player ->
                    OpponentTile(player, ui, onOpen = { onOpen(player) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OpponentTile(
    user    : UserState,
    ui      : SessionUiState,
    onOpen  : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dead     = user.isDead(ui)
    val monarch  = ui.monarch == user.id
    Box(
        modifier = modifier
            .alpha(if (user.online) 1f else 0.6f)
            .raised()
            .combinedClickable(
                onClickLabel = Strings.playerShowDetails(user.displayName),
                onClick      = onOpen,
                onLongClick  = onOpen,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text     = if (monarch) "👑 " + user.displayName else user.displayName,
                    style    = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text       = user.life.toString(),
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (dead) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SymbolValue(COMMANDER_STAT, user.customStats[COMMANDER_STAT] ?: 0u)
                    SymbolValue(POISON_STAT, user.customStats[POISON_STAT] ?: 0u)
                }
                if (!user.online) {
                    Text(Strings.playerOffline, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (dead || user.conceded) {
                Box(
                    modifier         = Modifier.matchParentSize().background(Color.Black.copy(alpha = if (dead) 0.55f else 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (dead) "💀" else "🏳️", fontSize = 40.sp)
                }
            }
        }
    }
}

/** A small symbol with its number, as on the opponent tiles. */
@Composable
private fun SymbolValue(stat: String, value: UInt) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        StatIcon(stat, Modifier.size(15.dp))
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** Everything about another player, with what you can do about them. */
@Composable
internal fun PlayerDetailsDialog(
    user       : UserState,
    ui         : SessionUiState,
    isFriend   : Boolean,
    onAddFriend: (() -> Unit)?,
    onRemove   : (() -> Unit)?,
    onDismiss  : () -> Unit,
) {
    val settings = ui.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.displayName) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    user.isDead(ui) -> Text(Strings.playerDead, color = MaterialTheme.colorScheme.error)
                    user.conceded   -> Text(Strings.playerConceded)
                }
                if (!user.online) Text(Strings.playerOffline, style = MaterialTheme.typography.labelMedium)
                DetailRow(Strings.statLife, user.life.toString())
                DetailRow(Strings.statCommander,
                    "${user.customStats[COMMANDER_STAT] ?: 0u} / ${settings.commanderDeathThreshold}")
                DetailRow(Strings.statPoison,
                    "${user.customStats[POISON_STAT] ?: 0u} / ${settings.infectDeathThreshold}")
                if (ui.monarch == user.id) DetailRow("👑", Strings.playerMonarch)
                user.stats.filterKeys { it != COMMANDER_STAT && it != POISON_STAT }.forEach { (name, type) ->
                    val value = user.customStats[name] ?: 0u
                    DetailRow(statLabel(name), statValueText(type, value))
                }
                if (onAddFriend != null && !isFriend) {
                    OutlinedButton(onClick = { onAddFriend(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(Strings.playerSendFriendRequest)
                    }
                }
                if (onRemove != null) {
                    OutlinedButton(
                        onClick  = { onDismiss(); onRemove() },
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(Strings.playerRemove(user.displayName)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.actionClose) } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** How a stat's value reads in words: a number, on/off, or a ring stage. */
internal fun statValueText(type: StatType, value: UInt): String = when (type) {
    StatType.NUMERIC    -> value.toString()
    StatType.TOGGLE     -> if (value > 0u) Strings.statActive else Strings.statInactive
    StatType.RING_STAGE -> "$value / 4"
}
