package com.androsaver.visualizer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Running average of one band and the band's deviation above that average.
 *
 * Port of the psysuals 3.23 `BandLevel`. The average must hold through a
 * pause. Otherwise the first block of returning music reads about forty
 * times its normal level. It must still follow a quieter passage. A fixed
 * ratio cannot tell the two apart, so the band's noise floor is tracked as
 * well: the 20th percentile of the last second, remembered as its minimum and
 * allowed to rise only over minutes. A pause is judged on the median of the
 * last second and holds the average for as long as it lasts. A level below
 * 30 % of the average but above the noise is held for at most [HOLD_SECONDS]
 * and then followed. When the band jumps far above the average, after a pause
 * or a silent start, the average catches up quickly.
 *
 * The average is seeded from the median of the first second, and the level
 * reads 0 until then. Android effects expect a deviation (0 at steady music,
 * positive on peaks), so [update] returns `ratio - 1` with the ratio clipped
 * to 0..6 like upstream. The result therefore stays within 0..5.
 *
 * The class allocates nothing after construction.
 */
internal class AudioBandLevel(blocksPerSecond: Float) {
    companion object {
        const val HOLD_SECONDS = 5f
        const val PAUSE_ENTER = 3f
        const val PAUSE_EXIT = 5f
        const val FLOOR_RISE = 1.00005f
        const val MAX_RATIO = 6f
    }

    private val maxHeld = max(1, (HOLD_SECONDS * blocksPerSecond).roundToInt())
    private val window = FloatArray(max(4, blocksPerSecond.roundToInt()))
    private val sorted = FloatArray(window.size)
    private var count = 0
    private var next = 0

    /** Current running average, or a negative value until it is seeded. */
    internal var average = -1f; private set
    internal var floor = -1f; private set
    internal var paused = false; private set
    /** The upstream level: value / average, clipped to 0..[MAX_RATIO]. */
    internal var ratio = 0f; private set
    private var held = 0

    fun reset() {
        average = -1f; floor = -1f; held = 0; paused = false; ratio = 0f
        count = 0; next = 0
        window.fill(0f)
    }

    fun update(input: Float): Float {
        val value = if (input.isFinite()) max(0f, input) else 0f
        window[next] = value
        next = (next + 1) % window.size
        if (count < window.size) count++
        val full = count == window.size
        if (average < 0f) {
            if (!full) { ratio = 0f; return 0f }
            average = max(percentile(.5f), 1e-4f)
        }
        if (full) {
            val low = max(percentile(.2f), 1e-6f)
            floor = if (floor < 0f) low else min(floor * FLOOR_RISE, low)
            val median = percentile(.5f)
            paused = if (paused) {
                median < PAUSE_EXIT * floor
            } else {
                // Only a floor well below the average is a real noise floor;
                // a start in the middle of music seeds it at the music level.
                floor < .3f * average && median < PAUSE_ENTER * floor && median < .3f * average
            }
        }
        if (paused) {
            held = 0
        } else if (value < .3f * average && held < maxHeld) {
            held++
        } else {
            if (value >= .3f * average) held = 0
            average = if (value > 3f * average) average * .8f + value * .2f
                      else max(average * .98f + value * .02f, 1e-6f)
        }
        ratio = (value / (average + 1e-6f)).coerceIn(0f, MAX_RATIO)
        return max(0f, ratio - 1f)
    }

    /** Linear-interpolated percentile of the full window, as numpy computes it. */
    private fun percentile(q: Float): Float {
        System.arraycopy(window, 0, sorted, 0, window.size)
        sorted.sort()
        val pos = q * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }
}
