package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground

// The session screen's surfaces stand out from the background as raised, rounded slabs
// (cards, tabs, the add button) or sit pressed into them (values, the selected tab).
// Over a background image they are translucent, so the image shows through.

internal val CardShape = RoundedCornerShape(18.dp)

/** How opaque the surfaces are over a background image. */
private const val OVER_IMAGE_ALPHA = 0.55f

/** [color] as the session surfaces draw it: see-through when there is a background image. */
@Composable
private fun surfaceColor(color: Color): Color =
    if (LocalHasBackground.current) color.copy(alpha = color.alpha * OVER_IMAGE_ALPHA) else color

/**
 * A raised slab: a light top edge and a darker bottom edge, with a drop shadow on a plain
 * background. Over an image the shadow is left out, as it would show through the slab.
 */
@Composable
internal fun Modifier.raised(
    shape    : Shape = CardShape,
    elevation: Dp    = 8.dp,
    color    : Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier = this
    .then(if (LocalHasBackground.current) Modifier else Modifier.shadow(elevation, shape, clip = false))
    .clip(shape)
    .background(surfaceColor(color))
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.25f)),
        ),
        shape = shape,
    )

/** A pressed-in well: the reverse of [raised], dark on top and light at the bottom. */
@Composable
internal fun Modifier.inset(
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
): Modifier = this
    .clip(shape)
    .background(surfaceColor(color))
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent, Color.White.copy(alpha = 0.35f)),
        ),
        shape = shape,
    )
