package com.androsaver.visualizer

import kotlin.math.min
import kotlin.math.roundToInt

/** Shared visualizer tuning used by both legacy and psysuals effect ports. */
internal object VisualizerRenderTuning {
    const val MIN_TRAIL_FADE = 48f / 255f
    const val FAST_TRAIL_FADE = 0.20f
    private const val TV_REFERENCE_EDGE = 1080f

    fun viewportScale(width: Int, height: Int): Float =
        (min(width, height).coerceAtLeast(1) / TV_REFERENCE_EDGE).coerceIn(0.5f, 2f)

    fun fieldColumns(width: Int, height: Int): Int = when {
        min(width, height) >= 1800 -> 40
        min(width, height) >= 1000 -> 32
        else -> 24
    }

    fun fieldRows(width: Int, height: Int): Int =
        (fieldColumns(width, height) * height.coerceAtLeast(1).toFloat() /
            width.coerceAtLeast(1)).roundToInt().coerceIn(12, 28)

    fun fadeTrail(draw: GLDraw) = draw.fadeBlack(FAST_TRAIL_FADE)
}
