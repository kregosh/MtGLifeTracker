package com.kregosh.mtglifetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun CropMapping.centre(orb: OrbSpot) = Offset(centreX(orb), centreY(orb))

// ── colours ───────────────────────────────────────────────────────────────────

private class ManaColor(val core: Color, val glow: Color)

private val MANA_COLORS = listOf(
    ManaColor(core = Color(0xFFFFFDF2), glow = Color(0xFFE8E2FF)), // white
    ManaColor(core = Color(0xFFB9D4FF), glow = Color(0xFF3A6BFF)), // blue
    ManaColor(core = Color(0xFFCDB4F0), glow = Color(0xFF5A2A8C)), // black: a sickly violet
    ManaColor(core = Color(0xFFFFC2A8), glow = Color(0xFFE8361E)), // red
    ManaColor(core = Color(0xFFD2FFB8), glow = Color(0xFF2FBF4A)), // green
)

// ── particles ─────────────────────────────────────────────────────────────────

private class Mote(
    val orb      : Int,
    val start    : Offset,
    val velocity : Offset,   // px per second
    val wobble   : Float,    // px, sideways sway
    val size     : Float,    // px, core radius
    val bornNs   : Long,
    val lifeNs   : Long,
    val phase    : Float,
    val glints   : Boolean,  // some motes flash a four-point star
)

private class Flare(val orb: Int, val bornNs: Long)

private const val FLARE_NS = 600_000_000L

private fun spawnMotes(
    orbIndex: Int, centre: Offset, radius: Float, density: Float, nowNs: Long, rng: Random,
): List<Mote> = List(rng.nextInt(6, 13)) {
    val angle = rng.nextFloat() * 2f * PI.toFloat()
    val dir   = Offset(cos(angle), sin(angle))
    val speed = (18f + rng.nextFloat() * 42f) * density
    Mote(
        orb      = orbIndex,
        start    = centre + dir * (radius * (0.85f + rng.nextFloat() * 0.2f)),
        // Outwards, with an eerie drift upwards.
        velocity = dir * speed + Offset(0f, -14f * density),
        wobble   = (3f + rng.nextFloat() * 6f) * density,
        size     = (1.2f + rng.nextFloat() * 2.2f) * density,
        bornNs   = nowNs,
        lifeNs   = (700L + rng.nextLong(900L)) * 1_000_000L,
        phase    = rng.nextFloat() * 2f * PI.toFloat(),
        glints   = rng.nextFloat() < 0.3f,
    )
}

private fun DrawScope.drawFlare(centre: Offset, radius: Float, color: ManaColor, age: Float) {
    val a = (1f - age).coerceIn(0f, 1f)
    drawCircle(
        brush  = Brush.radialGradient(
            0.55f to Color.Transparent,
            0.75f to color.glow.copy(alpha = 0.32f * a),
            1.0f  to Color.Transparent,
            center = centre,
            radius = radius * 1.45f,
        ),
        radius = radius * 1.45f,
        center = centre,
    )
}

private fun DrawScope.drawMote(mote: Mote, color: ManaColor, nowNs: Long) {
    val t = (nowNs - mote.bornNs).toFloat() / mote.lifeNs
    if (t !in 0f..1f) return
    val secs = (nowNs - mote.bornNs) / 1e9f

    // Quick fade in, long fade out, with a flicker on top.
    val envelope = if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f
    val twinkle  = 0.55f + 0.45f * sin(secs * 18f + mote.phase)
    val alpha    = (envelope * twinkle).coerceIn(0f, 1f)

    val sway = Offset(-mote.velocity.y, mote.velocity.x).let { perp ->
        val len = perp.getDistance().coerceAtLeast(1f)
        perp / len * (mote.wobble * sin(secs * 6f + mote.phase))
    }
    val pos  = mote.start + mote.velocity * secs + sway
    val size = mote.size * (1f - 0.4f * t)

    drawCircle(
        brush  = Brush.radialGradient(
            listOf(color.glow.copy(alpha = 0.55f * alpha), Color.Transparent),
            center = pos,
            radius = size * 5f,
        ),
        radius = size * 5f,
        center = pos,
    )
    drawCircle(color.core.copy(alpha = alpha), radius = size, center = pos)

    if (mote.glints && twinkle > 0.8f) {
        val arm   = size * 4f
        val glint = color.core.copy(alpha = alpha * 0.8f)
        drawLine(glint, pos - Offset(arm, 0f), pos + Offset(arm, 0f), strokeWidth = size * 0.45f, cap = StrokeCap.Round)
        drawLine(glint, pos - Offset(0f, arm), pos + Offset(0f, arm), strokeWidth = size * 0.45f, cap = StrokeCap.Round)
    }
}

// ── composable ────────────────────────────────────────────────────────────────

/**
 * Eerie sparks drifting off the mana orbs of the Mana Orbs background, in each orb's colour.
 * [imageWidth] × [imageHeight] is the background bitmap, drawn full-screen with ContentScale.Crop.
 */
@Composable
fun ManaSparkOverlay(imageWidth: Int, imageHeight: Int, dark: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val orbs    = remember(dark) { manaOrbSpots(dark) }

    var viewW   by remember { mutableFloatStateOf(0f) }
    var viewH   by remember { mutableFloatStateOf(0f) }
    var nowNs   by remember { mutableLongStateOf(0L) }
    val motes   = remember { mutableListOf<Mote>() }
    val flares  = remember { mutableListOf<Flare>() }

    // Frame clock: advances the animation and drops what has faded out.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frame ->
                nowNs = frame
                motes.removeAll { frame - it.bornNs > it.lifeNs }
                flares.removeAll { frame - it.bornNs > FLARE_NS }
            }
        }
    }

    // Emitter: a burst from one or more orbs every 0.2–0.5 s.
    LaunchedEffect(imageWidth, imageHeight, dark) {
        while (true) {
            delay(nextBurstDelayMs(Random))
            if (viewW == 0f || viewH == 0f || nowNs == 0L) continue
            val mapping = CropMapping(imageWidth.toFloat(), imageHeight.toFloat(), viewW, viewH)
            pickOrbs(Random, orbs.size).forEach { i ->
                motes  += spawnMotes(i, mapping.centre(orbs[i]), mapping.radius(orbs[i]), density, nowNs, Random)
                flares += Flare(i, nowNs)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewW = size.width.toFloat()
                viewH = size.height.toFloat()
            }
    ) {
        val now = nowNs
        if (now == 0L || viewW == 0f) return@Canvas
        val mapping = CropMapping(imageWidth.toFloat(), imageHeight.toFloat(), size.width, size.height)
        flares.forEach { f ->
            val orb = orbs[f.orb]
            drawFlare(mapping.centre(orb), mapping.radius(orb), MANA_COLORS[f.orb], (now - f.bornNs).toFloat() / FLARE_NS)
        }
        motes.forEach { drawMote(it, MANA_COLORS[it.orb], now) }
    }
}
