package com.kregosh.mtglifetracker.ui.components

import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

// Where the Mana Orbs background's orbs are, and when they spark. No Compose here, so it's unit-testable.

// ── where the orbs are ────────────────────────────────────────────────────────

/** An orb in the background image: centre as fractions of the image, radius as a fraction of its width. */
internal data class OrbSpot(val x: Float, val y: Float, val radius: Float)

/** The five orbs, left to right: white, blue, black, red, green. The two themes' images differ slightly. */
internal fun manaOrbSpots(dark: Boolean): List<OrbSpot> =
    if (dark) listOf(
        OrbSpot(0.124f, 0.530f, 0.105f),
        OrbSpot(0.244f, 0.472f, 0.105f),
        OrbSpot(0.497f, 0.451f, 0.105f),
        OrbSpot(0.759f, 0.472f, 0.105f),
        OrbSpot(0.868f, 0.536f, 0.105f),
    ) else listOf(
        OrbSpot(0.138f, 0.531f, 0.105f),
        OrbSpot(0.250f, 0.463f, 0.105f),
        OrbSpot(0.497f, 0.432f, 0.105f),
        OrbSpot(0.751f, 0.463f, 0.105f),
        OrbSpot(0.866f, 0.531f, 0.105f),
    )

/** The background is drawn with ContentScale.Crop: scaled to cover the view and centred. */
internal class CropMapping(imageW: Float, imageH: Float, viewW: Float, viewH: Float) {
    val scale   = max(viewW / imageW, viewH / imageH)
    private val dx = (viewW - imageW * scale) / 2f
    private val dy = (viewH - imageH * scale) / 2f
    private val iw = imageW
    private val ih = imageH

    fun centreX(orb: OrbSpot) = orb.x * iw * scale + dx
    fun centreY(orb: OrbSpot) = orb.y * ih * scale + dy
    fun radius(orb: OrbSpot)  = orb.radius * iw * scale
}

// ── intensity ─────────────────────────────────────────────────────────────────

/** Every orb always sparkles at least this much, sparkles per second. */
internal const val BASE_SPARKLES_PER_SECOND = 1.5f

/** The busiest a pulse can get, on top of the base rate. */
internal const val MIN_PULSE_PEAK = 3f
internal const val MAX_PULSE_PEAK = 12f

/**
 * One swell of an orb's sparkling: the rate rises and falls along a bell curve over
 * [durationMs], peaking at [peak] in the middle. Each orb runs its own endless chain of
 * pulses with random lengths and strengths, so the orbs' intensities drift independently.
 */
internal data class Pulse(val durationMs: Long, val peak: Float)

internal fun nextPulse(rng: Random): Pulse = Pulse(
    durationMs = rng.nextLong(2_500L, 6_001L),
    peak       = MIN_PULSE_PEAK + rng.nextFloat() * (MAX_PULSE_PEAK - MIN_PULSE_PEAK),
)

/** Sparkles per second from an orb [elapsedMs] into [pulse]: the base rate plus the swell. */
internal fun orbRate(elapsedMs: Float, pulse: Pulse): Float =
    BASE_SPARKLES_PER_SECOND + swell(elapsedMs, pulse.durationMs.toFloat(), pulse.peak)

/**
 * The bell-curve part of the rate: a Gaussian centred on the middle of [durationMs], with σ
 * a sixth of the duration, so it starts and ends at about 1% of [peak]; zero outside.
 */
internal fun swell(elapsedMs: Float, durationMs: Float, peak: Float): Float {
    if (elapsedMs < 0f || elapsedMs > durationMs || durationMs <= 0f) return 0f
    val mean  = durationMs / 2f
    val sigma = durationMs / 6f
    val z     = (elapsedMs - mean) / sigma
    return peak * exp(-0.5f * z * z)
}
