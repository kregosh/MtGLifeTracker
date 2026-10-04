package com.kregosh.mtglifetracker.ui

import kotlin.math.pow

// Picking black or white text from how bright the background behind it is. No Android or
// Compose types here, so it's unit-testable.

/** The top part of the background image, where the timer and other text sit on it directly. */
const val BACKDROP_TEXT_BAND = 0.3f

/**
 * Background luminance above which black text contrasts better than white (WCAG): black
 * and white have equal contrast ratios against a luminance of about 0.179.
 */
const val CONTRAST_CROSSOVER = 0.179f

/** WCAG relative luminance (0 = black, 1 = white) of an ARGB pixel. */
fun relativeLuminance(argb: Int): Float {
    fun channel(shift: Int): Float {
        val c = ((argb shr shift) and 0xFF) / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
}

/** Average luminance of [pixels]; 0 for none. */
fun averageLuminance(pixels: IntArray): Float =
    if (pixels.isEmpty()) 0f else pixels.fold(0f) { sum, p -> sum + relativeLuminance(p) } / pixels.size

/** Whether text over a background of [luminance] reads better in black than in white. */
fun prefersDarkText(luminance: Float): Boolean = luminance > CONTRAST_CROSSOVER
