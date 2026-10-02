package com.kregosh.mtglifetracker.ui

import kotlin.time.Duration

/** m:ss under an hour (zero-padded minutes), h:mm:ss from an hour on. */
internal fun Duration.toTimerString(): String {
    val total = inWholeSeconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** A life delta as shown to players, with a typographic minus: "+3", "−2". */
internal fun Int.signed(): String = if (this > 0) "+$this" else "−${-this}"
