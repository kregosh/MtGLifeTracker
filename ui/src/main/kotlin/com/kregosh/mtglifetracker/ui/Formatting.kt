package com.kregosh.mtglifetracker.ui

import kotlin.time.Duration

/** m:ss under an hour (zero-padded minutes), h:mm:ss from an hour on. */
internal fun Duration.toTimerString(): String {
    val total = inWholeSeconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
}

/** A life delta as shown to players, with a typographic minus: "+3", "−2". */
internal fun Int.signed(): String = if (this > 0) "+$this" else "−${-this}"
