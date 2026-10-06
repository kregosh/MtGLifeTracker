package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT
import com.kregosh.mtglifetracker.shared.UserState
import com.kregosh.mtglifetracker.ui.Strings
import com.kregosh.mtglifetracker.viewmodel.SessionUiState
import com.kregosh.mtglifetracker.viewmodel.isDead

/** From this width on, a watching device shows the table view instead of the tile strip. */
internal val TABLE_VIEW_MIN_WIDTH: Dp = 600.dp

/**
 * For a watching tablet lying in the middle of the table: every player in detail, in two rows
 * for the two sides of the table. The far row is turned around to face the players sitting
 * there. Four players make the 2×2 of a Commander game.
 */
@Composable
internal fun TableView(
    players : List<UserState>,
    ui      : SessionUiState,
    onOpen  : (UserState) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (players.isEmpty()) return
    val far  = players.take((players.size + 1) / 2)
    val near = players.drop(far.size)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(far to true, near to false).filter { it.first.isNotEmpty() }.forEach { (row, turned) ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { player ->
                    TableCard(
                        user     = player,
                        ui       = ui,
                        turned   = turned,
                        onOpen   = { onOpen(player) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun TableCard(
    user    : UserState,
    ui      : SessionUiState,
    turned  : Boolean,
    onOpen  : () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dead    = user.isDead(ui)
    val monarch = ui.monarch == user.id
    Box(
        modifier = modifier
            .graphicsLayer { if (turned) rotationZ = 180f }
            .alpha(if (user.online) 1f else 0.6f)
            .raised(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClickLabel = Strings.playerShowDetails(user.displayName),
                onClick      = onOpen,
                onLongClick  = onOpen,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text       = if (monarch) "👑 " + user.displayName else user.displayName,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (!user.online) Text(Strings.playerOffline, style = MaterialTheme.typography.labelMedium)

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val size = (maxHeight.value * 0.6f).coerceIn(40f, 160f)
                    StatIcon(LIFE_STAT, Modifier.size((size * 1.4f).dp).alpha(0.10f))
                    Text(
                        text       = user.life.toString(),
                        fontSize   = size.sp,
                        lineHeight = size.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (dead) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    LimitValue(COMMANDER_STAT, user.customStats[COMMANDER_STAT] ?: 0u, ui.settings.commanderDeathThreshold)
                    LimitValue(POISON_STAT, user.customStats[POISON_STAT] ?: 0u, ui.settings.infectDeathThreshold)
                }

                val minor = user.stats.filterKeys { it != COMMANDER_STAT && it != POISON_STAT }
                if (minor.isNotEmpty()) {
                    FlowRow(
                        modifier              = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                    ) {
                        minor.forEach { (name, type) ->
                            Text(
                                text     = "${statShortLabel(name)} ${statValueText(type, user.customStats[name] ?: 0u)}",
                                style    = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.inset(RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (dead || user.conceded) {
            Box(
                modifier         = Modifier.matchParentSize().background(Color.Black.copy(alpha = if (dead) 0.55f else 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (dead) "💀" else "🏳️", fontSize = 72.sp)
            }
        }
    }
}

/** A symbol with its value out of the limit that knocks a player out, e.g. 5/21. */
@Composable
private fun LimitValue(stat: String, value: UInt, limit: UInt) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatIcon(stat, Modifier.size(28.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("/$limit", fontSize = 14.sp, color = LocalContentColor.current.copy(alpha = 0.6f),
                 modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
