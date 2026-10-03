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

private fun spawnMote(
    orbIndex: Int, centre: Offset, radius: Float, density: Float, nowNs: Long, rng: Random,
): Mote {
    val angle = rng.nextFloat() * 2f * PI.toFloat()
    val dir   = Offset(cos(angle), sin(angle))
    val speed = (10f + rng.nextFloat() * 22f) * density
    return Mote(
        orb      = orbIndex,
        start    = centre + dir * (radius * (0.85f + rng.nextFloat() * 0.2f)),
        // Outwards, with an eerie drift upwards.
        velocity = dir * speed + Offset(0f, -10f * density),
        wobble   = (3f + rng.nextFloat() * 6f) * density,
        size     = (1.2f + rng.nextFloat() * 2.2f) * density,
        bornNs   = nowNs,
        lifeNs   = (1_200L + rng.nextLong(1_200L)) * 1_000_000L,
        phase    = rng.nextFloat() * 2f * PI.toFloat(),
        glints   = rng.nextFloat() < 0.3f,
    )
}

/**
 * The current emission phase and how far each of its orbs is towards its next sparkle.
 * Each orb runs a little faster or slower than the curve, so they don't pulse in step.
 */
private class Emitter(val startNs: Long, val phase: EmissionPhase, rng: Random) {
    val pace    = phase.orbs.associateWith { 0.8f + rng.nextFloat() * 0.4f }
    val pending = phase.orbs.associateWith { 0f }.toMutableMap()

    fun elapsedMs(nowNs: Long) = (nowNs - startNs) / 1e6f
    fun over(nowNs: Long)      = elapsedMs(nowNs) > phase.totalMs
}

/** The halo swells and fades with the orb's emission; [intensity] is 0..1. */
private fun DrawScope.drawHalo(centre: Offset, radius: Float, color: ManaColor, intensity: Float) {
    val a = intensity.coerceIn(0f, 1f)
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
 * One or a few orbs at a time emit for 3–5 s, swelling and fading, then a short pause.
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
    var emitter by remember { mutableStateOf<Emitter?>(null) }

    // Frame clock: runs the emission phases, spawns sparkles along each phase's bell
    // curve, and drops what has faded out.
    LaunchedEffect(imageWidth, imageHeight, dark) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { frame ->
                val dtSecs = if (lastNs == 0L) 0f else ((frame - lastNs) / 1e9f).coerceAtMost(0.1f)
                lastNs = frame
                nowNs  = frame
                motes.removeAll { frame - it.bornNs > it.lifeNs }

                val current = emitter?.takeUnless { it.over(frame) }
                    ?: Emitter(frame, nextPhase(Random, orbs.size), Random).also { emitter = it }
                if (viewW == 0f || viewH == 0f) return@withFrameNanos

                val rate    = emissionRate(current.elapsedMs(frame), current.phase.durationMs.toFloat())
                val mapping = CropMapping(imageWidth.toFloat(), imageHeight.toFloat(), viewW, viewH)
                current.phase.orbs.forEach { i ->
                    var due = current.pending.getValue(i) + rate * current.pace.getValue(i) * dtSecs
                    while (due >= 1f) {
                        motes += spawnMote(i, mapping.centre(orbs[i]), mapping.radius(orbs[i]), density, frame, Random)
                        due -= 1f
                    }
                    current.pending[i] = due
                }
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
        emitter?.let { e ->
            val intensity = emissionRate(e.elapsedMs(now), e.phase.durationMs.toFloat()) / PEAK_SPARKLES_PER_SECOND
            e.phase.orbs.forEach { i ->
                drawHalo(mapping.centre(orbs[i]), mapping.radius(orbs[i]), MANA_COLORS[i], intensity)
            }
        }
        motes.forEach { drawMote(it, MANA_COLORS[it.orb], now) }
    }
}
