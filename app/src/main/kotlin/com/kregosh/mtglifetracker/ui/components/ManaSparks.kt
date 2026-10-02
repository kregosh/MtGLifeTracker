package com.kregosh.mtglifetracker.ui.components

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

// ── timing ────────────────────────────────────────────────────────────────────

/** Bursts come every 0.2–0.5 s: far more often than the storm's lightning, but never in step. */
internal fun nextBurstDelayMs(rng: Random): Long = rng.nextLong(200L, 501L)

/** Mostly one orb at a time, sometimes two or three, never the same orb twice in a burst. */
internal fun pickOrbs(rng: Random, orbCount: Int = 5): List<Int> {
    val roll  = rng.nextFloat()
    val count = when {
        roll < 0.6f -> 1
        roll < 0.9f -> 2
        else        -> 3
    }.coerceAtMost(orbCount)
    return (0 until orbCount).shuffled(rng).take(count)
}
