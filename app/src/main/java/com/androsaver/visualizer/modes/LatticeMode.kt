package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Lattice — crystal grid of glowing nodes and beam lines.
 * Port of psysuals Lattice (v2.9.0+).
 *
 * A 14×9 grid of nodes glows according to their assigned FFT frequency bin
 * (bass on the left columns, treble on the right). Thin double-stroke beams
 * connect adjacent nodes; beam brightness tracks local spectral energy. On
 * every strong beat a shockwave ring expands from the centre — nodes near
 * the wavefront flare white. Hue rotates slowly with a radial colour offset.
 *
 *   FFT bins → per-node brightness (spatial frequency map across the grid)
 *   Bass     → subtle whole-grid scale breath
 *   Beat     → shockwave ring + node flare at the wavefront
 */
class LatticeMode : BaseMode() {

    override val name = "Lattice"

    private companion object {
        private const val COLS        = 14
        private const val ROWS        = 9
        private const val N_NODES     = COLS * ROWS   // 126
        private const val FFT_USE     = 0.55f
        private const val SHOCK_W     = 22f
        private const val IDLE        = 0.08f
        private const val TRAIL_ALPHA = 20f / 255f
    }

    // Audio state
    private var hue      = 0.52f
    private var shockR   = 9999f
    private var beatPrev = 0f
    private var scale    = 1f
    private var svel     = 0f

    // Grid geometry — rebuilt when W or H changes
    private var cx = 0f;  private var cy = 0f
    private var maxR = 0f
    private val nodeOx   = FloatArray(N_NODES)
    private val nodeOy   = FloatArray(N_NODES)
    private val nodeCol  = IntArray(N_NODES)
    private val nodeHOff = FloatArray(N_NODES)
    private var cachedW  = 0;  private var cachedH = 0

    // Per-frame scratch arrays (avoid alloc in hot path)
    private val sxArr  = FloatArray(N_NODES)
    private val syArr  = FloatArray(N_NODES)
    private val bright = FloatArray(N_NODES)

    private fun initGrid(W: Int, H: Int) {
        if (W == cachedW && H == cachedH) return
        cachedW = W;  cachedH = H
        val Wf = W.toFloat();  val Hf = H.toFloat()
        cx = Wf / 2f;  cy = Hf / 2f
        maxR = hypot(cx, cy)
        val x0 = Wf * 0.08f
        val y0 = Hf * 0.10f
        val cw = Wf * 0.84f / (COLS - 1).toFloat()
        val ch = Hf * 0.80f / (ROWS - 1).toFloat()
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val ni   = row * COLS + col
                val ox   = x0 + col.toFloat() * cw
                val oy   = y0 + row.toFloat() * ch
                nodeOx[ni]   = ox
                nodeOy[ni]   = oy
                nodeCol[ni]  = col
                // Radial hue offset: 0 at centre → 0.55 at corner
                nodeHOff[ni] = hypot(ox - cx, oy - cy) / maxR * 0.55f
            }
        }
    }

    /** Map grid column to FFT bin index. */
    private fun fftBin(col: Int, fftLen: Int): Int {
        val maxBin = min(fftLen - 1, (fftLen.toFloat() * FFT_USE).toInt())
        return (col.toFloat() / (COLS - 1).toFloat() * maxBin.toFloat()).toInt()
    }

    override fun reset() {
        hue      = 0.52f
        shockR   = 9999f
        beatPrev = 0f
        scale    = 1f
        svel     = 0f
        cachedW  = 0;  cachedH = 0
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W      = draw.W;  val H = draw.H
        val fft    = audio.fft
        val fftLen = fft.size
        val bass   = fft.meanSlice(0, 6)
        val mid    = fft.meanSlice(6, 30)
        val beat   = audio.beat

        draw.fadeBlack(TRAIL_ALPHA)
        initGrid(W, H)

        hue = (hue + 0.0025f + mid * 0.001f) % 1f

        // Beat fires shockwave
        if (beat > 0.6f && beatPrev <= 0.6f) shockR = 0f
        beatPrev = beat
        shockR  += 6f * (1f + bass * 2f + beat * 0.8f)

        // Bass-driven scale breath
        svel  += (1f + bass * 0.04f - scale) * 0.18f
        svel  *= 0.70f
        scale  = (scale + svel).coerceIn(0.90f, 1.12f)

        val sc = scale

        // ── Compute per-node position and brightness ─────────────────────────
        for (ni in 0 until N_NODES) {
            val sx = cx + (nodeOx[ni] - cx) * sc
            val sy = cy + (nodeOy[ni] - cy) * sc
            sxArr[ni] = sx;  syArr[ni] = sy

            val energy = fft[fftBin(nodeCol[ni], fftLen)] + IDLE
            val dist   = hypot(sx - cx, sy - cy)
            val shock  = maxOf(0f, 1f - abs(dist - shockR) / SHOCK_W)
            bright[ni] = minOf(energy + shock * (0.6f + bass * 0.4f), 1.6f)
        }

        // ── Beams ─────────────────────────────────────────────────────────────
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val ni   = row * COLS + col
                val x0f  = sxArr[ni];  val y0f = syArr[ni]
                val nodeHue = (hue + nodeHOff[ni]) % 1f

                // Horizontal beam → right neighbour
                if (col < COLS - 1) {
                    val niR  = ni + 1
                    val avgB = (bright[ni] + bright[niR]) * 0.5f
                    if (avgB > 0.06f) {
                        val x1 = sxArr[niR];  val y1 = syArr[niR]
                        val lc = GLDraw.hsl(nodeHue, l = minOf(avgB * 0.22f, 0.38f))
                        val ic = GLDraw.hsl(nodeHue, l = minOf(avgB * 0.55f, 0.80f))
                        draw.line(x0f, y0f, x1, y1, lc[0], lc[1], lc[2], 0.65f)
                        draw.line(x0f, y0f, x1, y1, ic[0], ic[1], ic[2], 1f)
                    }
                }

                // Vertical beam → lower neighbour
                if (row < ROWS - 1) {
                    val niD  = ni + COLS
                    val avgB = (bright[ni] + bright[niD]) * 0.5f
                    if (avgB > 0.06f) {
                        val x1 = sxArr[niD];  val y1 = syArr[niD]
                        val lc = GLDraw.hsl(nodeHue, l = minOf(avgB * 0.18f, 0.32f))
                        val ic = GLDraw.hsl(nodeHue, l = minOf(avgB * 0.48f, 0.72f))
                        draw.line(x0f, y0f, x1, y1, lc[0], lc[1], lc[2], 0.65f)
                        draw.line(x0f, y0f, x1, y1, ic[0], ic[1], ic[2], 1f)
                    }
                }
            }
        }

        // ── Nodes ─────────────────────────────────────────────────────────────
        for (ni in 0 until N_NODES) {
            val sx      = sxArr[ni];  val sy = syArr[ni]
            val nodeHue = (hue + nodeHOff[ni]) % 1f
            val b       = bright[ni]

            val rGlow = maxOf(3f, 5f + b * 10f)
            val rCore = maxOf(1f, 2f + b * 4f)

            val gc = GLDraw.hsl(nodeHue, l = minOf(b * 0.22f, 0.38f))
            draw.circle(sx, sy, rGlow, gc[0], gc[1], gc[2], 1f, segments = 12)

            val cc = GLDraw.hsl(nodeHue, l = minOf(b * 0.65f + 0.15f, 0.92f))
            draw.circle(sx, sy, rCore, cc[0], cc[1], cc[2], 1f, segments = 8)

            // Shockwave flare: white-hot ring near wavefront
            val dist  = hypot(sx - cx, sy - cy)
            val shock = maxOf(0f, 1f - abs(dist - shockR) / SHOCK_W)
            if (shock > 0.12f) {
                val fr = maxOf(2f, rCore + shock * 9f)
                val fc = GLDraw.hsl(nodeHue, l = minOf(0.88f + shock * 0.12f, 1f))
                draw.circle(sx, sy, fr, fc[0], fc[1], fc[2], 1f, segments = 8)
            }
        }
    }
}
