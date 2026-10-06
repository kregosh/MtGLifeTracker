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
import androidx.compose.ui.draw.clip
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text       = if (monarch) "👑 " + user.displayName else user.displayName,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (!user.online) Text(Strings.playerOffline, style = MaterialTheme.typography.labelMedium)
                }

                // Life, commander damage and poison side by side, each as prominent as the others:
                // on a table any of them can end the game.
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBlock(LIFE_STAT, user.life, limit = null, dead = dead, modifier = Modifier.weight(1f).fillMaxHeight())
                    StatBlock(COMMANDER_STAT, user.customStats[COMMANDER_STAT] ?: 0u, ui.settings.commanderDeathThreshold,
                              modifier = Modifier.weight(1f).fillMaxHeight())
                    StatBlock(POISON_STAT, user.customStats[POISON_STAT] ?: 0u, ui.settings.infectDeathThreshold,
                              modifier = Modifier.weight(1f).fillMaxHeight())
                }

                val minor = user.stats.filterKeys { it != COMMANDER_STAT && it != POISON_STAT }
                if (minor.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                    ) {
                        minor.forEach { (name, type) ->
                            Text(
                                text     = "${statShortLabel(name)} ${statValueText(type, user.customStats[name] ?: 0u)}",
                                style    = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.inset(RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
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

/**
 * One counter as a well of its own: symbol, a number as large as the well allows and, for
 * counters with a limit, how close it is: "/21" and a meter that warms up as it fills.
 */
@Composable
private fun StatBlock(stat: String, value: UInt, limit: UInt?, modifier: Modifier, dead: Boolean = false) {
    BoxWithConstraints(modifier.inset(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        val numberSize = minOf(maxHeight.value * 0.42f, maxWidth.value * 0.42f).coerceIn(28f, 140f)
        val iconSize   = (numberSize * 0.4f).coerceIn(20f, 48f)
        Column(
            modifier            = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatIcon(stat, Modifier.size(iconSize.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = value.toString(),
                    fontSize   = numberSize.sp,
                    lineHeight = numberSize.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (dead) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
                if (limit != null) {
                    Text(
                        text     = "/$limit",
                        fontSize = (numberSize * 0.3f).sp,
                        color    = LocalContentColor.current.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = (numberSize * 0.1f).dp),
                    )
                }
            }
            if (limit != null) LimitMeter(value, limit, Modifier.fillMaxWidth(0.8f).height(10.dp))
            else Spacer(Modifier.height(10.dp))
        }
    }
}

/** How far [value] is toward [limit]: calm at first, amber from half way, red from three quarters. */
@Composable
private fun LimitMeter(value: UInt, limit: UInt, modifier: Modifier) {
    val fraction = if (limit == 0u) 1f else (value.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val color = when {
        fraction >= 0.75f -> MaterialTheme.colorScheme.error
        fraction >= 0.5f  -> Color(0xFFE0A030)
        else              -> LocalContentColor.current.copy(alpha = 0.55f)
    }
    val shape = RoundedCornerShape(50)
    Box(modifier.clip(shape).background(LocalContentColor.current.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(shape).background(color))
    }
}
