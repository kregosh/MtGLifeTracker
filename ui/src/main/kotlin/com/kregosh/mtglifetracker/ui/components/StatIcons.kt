package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.shared.COMMANDER_STAT
import com.kregosh.mtglifetracker.shared.LIFE_STAT
import com.kregosh.mtglifetracker.shared.POISON_STAT

// Symbols for the life-affecting counters, drawn as vectors so they look the same on Android
// and in the browser. More stats get theirs in #110.

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

/** Life: a heart. One colour, tinted by [StatIcon]. */
internal val HeartIcon = icon("Heart") {
    addPath(
        pathData = addPathNodes("M12 20s-7.5-4.6-7.5-10.2A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.4C19.5 15.4 12 20 12 20z"),
        fill     = SolidColor(Color.Black),
    )
}

/** Commander damage: a helmet. One colour, tinted by [StatIcon]. */
internal val HelmetIcon = icon("Helmet") {
    addPath(
        pathData        = addPathNodes("M5 19v-5c0-5.5 3.1-9 7-9s7 3.5 7 9v5h-4.5v-5.5h-5V19z M12 5V2.5"),
        stroke          = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineJoin  = StrokeJoin.Round,
        strokeLineCap   = StrokeCap.Round,
    )
}

/** Poison: a green-brown drop with a highlight, in its own colours. */
internal val PoisonDropIcon = icon("PoisonDrop") {
    addPath(
        pathData        = addPathNodes("M12 2.5C12 2.5 5 10.2 5 15a7 7 0 0 0 14 0c0-4.8-7-12.5-7-12.5z"),
        fill            = SolidColor(Color(0xFF6E7A2C)),
        stroke          = SolidColor(Color(0xFF3B4216)),
        strokeLineWidth = 1.5f,
    )
    addPath(
        pathData        = addPathNodes("M9 15.5a3 3 0 0 0 2.5 3"),
        stroke          = SolidColor(Color(0xFFC9D38A)),
        strokeLineWidth = 1.5f,
        strokeLineCap   = StrokeCap.Round,
    )
}

private val LIFE_RED_LIGHT      = Color(0xFFB23A3A)
private val LIFE_RED_DARK       = Color(0xFFE57373)
private val COMMANDER_RED_LIGHT = Color(0xFF8E1F1F)
private val COMMANDER_RED_DARK  = Color(0xFFEF9A9A)

/** Whether the current surface is dark, so tinted symbols can stay readable. */
@Composable
internal fun onDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Whether [stat] has a symbol of its own. */
internal fun hasStatIcon(stat: String): Boolean = stat == LIFE_STAT || stat == COMMANDER_STAT || stat == POISON_STAT

/** The symbol for [stat] (life, commander damage or poison); [dark] picks the tints for dark ground. */
@Composable
internal fun StatIcon(stat: String, modifier: Modifier = Modifier, dark: Boolean = onDarkSurface()) {
    when (stat) {
        LIFE_STAT      -> Icon(HeartIcon, contentDescription = null, modifier = modifier,
                               tint = if (dark) LIFE_RED_DARK else LIFE_RED_LIGHT)
        COMMANDER_STAT -> Icon(HelmetIcon, contentDescription = null, modifier = modifier,
                               tint = if (dark) COMMANDER_RED_DARK else COMMANDER_RED_LIGHT)
        POISON_STAT    -> Image(rememberVectorPainter(PoisonDropIcon), contentDescription = null, modifier = modifier)
    }
}
