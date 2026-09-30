package com.kregosh.mtglifetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

// ── data ─────────────────────────────────────────────────────────────────────

private data class BoltSpec(
    val points: List<Offset>,
    val branches: List<List<Offset>>,
)

// ── midpoint displacement ─────────────────────────────────────────────────────

private fun midpointBolt(
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    roughness: Float = 0.55f,
    depth: Int = 6,
    rng: Random = Random.Default,
): List<Offset> {
    var pts = listOf(Offset(x0, y0), Offset(x1, y1))
    var r = roughness
    repeat(depth) {
        val next = mutableListOf(pts[0])
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
            val len = hypot(b.x - a.x, b.y - a.y)
            val nx = -(b.y - a.y); val ny = b.x - a.x
            val nLen = hypot(nx, ny).coerceAtLeast(1f)
            val jitter = rng.nextFloat() * 2f - 1f
            next += Offset(mx + nx / nLen * jitter * len * r,
                           my + ny / nLen * jitter * len * r)
            next += b
        }
        pts = next
        r *= 0.6f
    }
    return pts
}

private fun makeBolt(canvasW: Float, canvasH: Float, rng: Random): BoltSpec {
    val startX = rng.nextFloat() * canvasW
    val endX   = startX + (rng.nextFloat() - 0.5f) * canvasW * 0.35f
    val pts    = midpointBolt(startX, 0f, endX, canvasH, depth = 7, rng = rng)
    // 1–2 branches
    val branches = (1..rng.nextInt(1, 3)).map {
        val bi  = rng.nextInt(pts.size / 4, pts.size * 2 / 3)
        val bp  = pts[bi]
        val bx2 = (bp.x + (rng.nextFloat() - 0.5f) * canvasW * 0.25f).coerceIn(0f, canvasW)
        val by2 = (bp.y + rng.nextFloat() * canvasH * 0.3f).coerceAtMost(canvasH)
        midpointBolt(bp.x, bp.y, bx2, by2, roughness = 0.45f, depth = 5, rng = rng)
    }
    return BoltSpec(pts, branches)
}

// ── drawing ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawBolt(
    pts: List<Offset>,
    alpha: Float,
    coreColor: Color,
    glowColor: Color,
    coreWidth: Dp,
    glowWidth: Dp,
) {
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    // outer glow
    drawPath(path, glowColor.copy(alpha = alpha * 0.35f),
        style = Stroke(width = glowWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    // mid glow
    drawPath(path, glowColor.copy(alpha = alpha * 0.55f),
        style = Stroke(width = (glowWidth.value / 2).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    // core
    drawPath(path, coreColor.copy(alpha = alpha),
        style = Stroke(width = coreWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    // bright centre
    drawPath(path, Color.White.copy(alpha = alpha * 0.7f),
        style = Stroke(width = (coreWidth.value * 0.4f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ── composable ────────────────────────────────────────────────────────────────

@Composable
fun LightningOverlay(modifier: Modifier = Modifier) {
    val alpha   = remember { Animatable(0f) }
    var bolt    by remember { mutableStateOf<BoltSpec?>(null) }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }
    val scope   = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            // wait a random 500 ms – 10 s between strikes
            delay(Random.nextLong(500L, 10_000L))
            if (canvasW == 0f || canvasH == 0f) continue
            bolt = makeBolt(canvasW, canvasH, Random)
            // flash in fast, fade out slower
            scope.launch {
                alpha.snapTo(0f)
                alpha.animateTo(1f,   androidx.compose.animation.core.tween(120))
                alpha.animateTo(0.6f, androidx.compose.animation.core.tween(80))
                alpha.animateTo(1f,   androidx.compose.animation.core.tween(60))
                alpha.animateTo(0f,   androidx.compose.animation.core.tween(350))
                bolt = null
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                canvasW = size.width.toFloat()
                canvasH = size.height.toFloat()
            }
    ) {
        val b = bolt ?: return@Canvas
        val a = alpha.value
        drawBolt(b.points, a, Color(0xFFD0C0FF), Color(0xFF7744FF), 2.dp, 18.dp)
        b.branches.forEach { branch ->
            drawBolt(branch, a * 0.75f, Color(0xFFB8A8F8), Color(0xFF5522CC), 1.2.dp, 10.dp)
        }
    }
}
