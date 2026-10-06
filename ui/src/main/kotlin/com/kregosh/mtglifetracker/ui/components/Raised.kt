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

// The session screen's surfaces stand out from the background as raised, rounded slabs
// (cards, tabs, the add button) or sit pressed into them (values, the selected tab).

internal val CardShape = RoundedCornerShape(18.dp)

/** A raised slab: a drop shadow, a light top edge and a darker bottom edge. */
@Composable
internal fun Modifier.raised(
    shape    : Shape = CardShape,
    elevation: Dp    = 8.dp,
    color    : Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier = this
    .shadow(elevation, shape, clip = false)
    .clip(shape)
    .background(color)
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
    .background(color)
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent, Color.White.copy(alpha = 0.35f)),
        ),
        shape = shape,
    )
