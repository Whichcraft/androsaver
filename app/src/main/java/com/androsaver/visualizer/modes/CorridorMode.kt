package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * CorridorMode — first-person neon rainbow corridor.
 * Concentric rounded-rectangle frames fly toward the camera with a full rainbow
 * sweep across depth; beat flares nearest frames and spawns glowing sparks.
 * Port of psysuals `Corridor` class (v1.4.1).
 *
 * Spark trailing: psysuals uses a dedicated spark_surf faded at alpha=10/255 per frame,
 * giving ~25 frames persistence.  Android has one framebuffer, so we maintain a
 * SPARK_HIST_LEN-frame ring buffer of spark screen snapshots and replay them with
 * additive blend at decreasing alpha — equivalent to _SPARK_FADE=10 in psysuals.
 */
class CorridorMode : BaseMode() {

    override val name = "Corridor"

    private companion object {
        const val N_FRAMES       = 28
        const val Z_FAR          = 12f
        const val Z_NEAR         = 0.28f
        const val WORLD_H        = 2.0f
        const val ASPECT         = 1.65f
        const val MAX_SPARKS     = 100
        const val TAU            = (Math.PI * 2).toFloat()
        // psysuals _SPARK_FADE=10 → ~255/10 = 25 frames persistence
        const val SPARK_HIST_LEN = 25
        // floats per spark snapshot: sx, sy, r, h, bright  (5 fields)
        const val SPARK_FIELDS   = 5
    }

    private data class Frame(var z: Float)
    // pt dropped — sparks evaluate path centre with time at draw time
    private data class Spark(var z: Float, val hue: Float, val ox: Float, val oy: Float)

    private val frames = ArrayList<Frame>(N_FRAMES)
    private val sparks = ArrayList<Spark>(MAX_SPARKS * 2)
    private var hue  = 0f
    private var time = 0f

    // Spark history ring buffer — each slot is a FloatArray of SPARK_FIELDS*N packed values
    private val sparkHist     = Array<FloatArray?>(SPARK_HIST_LEN) { null }
    private val sparkHistSize = IntArray(SPARK_HIST_LEN)   // actual spark count in each slot
    private var sparkHistHead = 0

    override fun reset() {
        hue = 0f; time = 0f
        frames.clear(); sparks.clear()
        val spacing = (Z_FAR - Z_NEAR) / N_FRAMES
        for (i in 0 until N_FRAMES) frames.add(Frame(Z_NEAR + i * spacing))
        for (i in 0 until SPARK_HIST_LEN) { sparkHist[i] = null; sparkHistSize[i] = 0 }
        sparkHistHead = 0
    }

    private fun path(t: Float): Pair<Float, Float> =
        sin(t * 0.19f) * 0.5f to cos(t * 0.14f) * 0.35f

    /**
     * Build a closed polygon (flat x0,y0,x1,y1,... array) tracing a rounded rectangle
     * centered at (cx,cy) with half-extents (hw, hh) and corner radius r.
     * Each corner arc uses [segs] segments.
     */
    private fun roundedRectPts(cx: Float, cy: Float, hw: Float, hh: Float,
                                r: Float, segs: Int = 6): FloatArray {
        val pts = FloatArray((segs + 1) * 4 * 2)
        var idx = 0
        val corners = floatArrayOf(
            cx + hw - r, cy + hh - r, 0f,            // bottom-right: arc 0→90°
            cx - hw + r, cy + hh - r, TAU * 0.25f,   // bottom-left:  arc 90→180°
            cx - hw + r, cy - hh + r, TAU * 0.50f,   // top-left:     arc 180→270°
            cx + hw - r, cy - hh + r, TAU * 0.75f,   // top-right:    arc 270→360°
        )
        for (ci in 0 until 4) {
            val ox = corners[ci * 3];  val oy = corners[ci * 3 + 1]
            val startAngle = corners[ci * 3 + 2]
            for (s in 0..segs) {
                val a = startAngle + s.toFloat() / segs * TAU * 0.25f
                pts[idx++] = ox + cos(a) * r
                pts[idx++] = oy + sin(a) * r
            }
        }
        return pts
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val fft  = audio.fft
        val beat = audio.beat
        val W = draw.W; val H = draw.H

        hue  += 0.005f
        val bass = fft.meanSlice(0, 6)
        val dt   = 0.028f + bass * 0.08f + beat * 0.16f
        time    += dt

        val fov = minOf(W, H) * 0.72f

        // ── Spawn sparks ──────────────────────────────────────────────────────
        // ox/oy bounded to ±85% of corridor half-extents so sparks stay inside
        val spawnN = (bass * 5f + beat * 4f).toInt()
        repeat(spawnN) {
            if (sparks.size < MAX_SPARKS * 2) {
                val z = Z_FAR * (0.55f + Math.random().toFloat() * 0.37f)
                sparks.add(Spark(
                    z   = z,
                    hue = (hue + Math.random().toFloat() * 0.6f) % 1f,
                    ox  = (Math.random().toFloat() * 2f - 1f) * WORLD_H * ASPECT * 0.85f,
                    oy  = (Math.random().toFloat() * 2f - 1f) * WORLD_H * 0.85f
                ))
            }
        }

        // ── Advance frames ────────────────────────────────────────────────────
        for (f in frames) {
            f.z -= dt
            if (f.z < Z_NEAR) f.z += Z_FAR
        }

        // ── Advance sparks, drop expired ──────────────────────────────────────
        val live = ArrayList<Spark>(sparks.size)
        for (sp in sparks) {
            sp.z -= dt
            if (sp.z >= Z_NEAR) live.add(sp)
        }
        sparks.clear()
        sparks.addAll(if (live.size > MAX_SPARKS) live.takeLast(MAX_SPARKS) else live)

        draw.fadeBlack(0.11f)

        // ── Draw frames (back to front, normal blend) ─────────────────────────
        // Sparks drawn after (additive blend) so they are always on top of frames.
        for (f in frames.sortedByDescending { it.z }) {
            val z     = maxOf(f.z, 0.01f)
            val nearT = maxOf(0f, 1f - z / Z_FAR)
            val (pcx, pcy) = path(time - z * 0.5f)
            val cxS = pcx * fov / z + W / 2f
            val cyS = pcy * fov / z + H / 2f

            val fi     = minOf((nearT * fft.size * 0.8f).toInt(), fft.size - 1)
            val h      = (hue + nearT) % 1f
            val bright = (0.06f + nearT * 0.70f + fft[fi] * 0.20f + beat * nearT * 0.50f)
                            .coerceIn(0f, 1f)

            val halfH = WORLD_H * fov / z
            val halfW = WORLD_H * ASPECT * fov / z
            if (halfW < 3f || halfH < 3f) continue

            val radius = maxOf(2f, minOf(halfW / 3f, halfH / 3f, halfW - 1f, halfH - 1f))

            val infl  = 4f
            val glR   = (radius + infl / 2f).coerceAtMost(minOf(halfW + infl, halfH + infl) - 1f)
            val glPts = roundedRectPts(cxS, cyS, halfW + infl, halfH + infl, maxOf(2f, glR))
            val gc    = GLDraw.hsl(h, l = bright * 0.22f)
            draw.polygon(glPts, gc[0], gc[1], gc[2], 0.7f)

            val mainPts = roundedRectPts(cxS, cyS, halfW, halfH, radius)
            val mc      = GLDraw.hsl(h, l = bright)
            draw.polygon(mainPts, mc[0], mc[1], mc[2], 1f)
        }

        // ── Compute current-frame spark screen positions ───────────────────────
        val curCount = sparks.size
        val needed   = curCount * SPARK_FIELDS
        var curBuf   = sparkHist[sparkHistHead]
        if (curBuf == null || curBuf.size < needed) {
            curBuf = FloatArray(maxOf(needed, MAX_SPARKS * SPARK_FIELDS))
        }
        var bi = 0
        for (sp in sparks) {
            val z     = maxOf(sp.z, 0.01f)
            val nearT = maxOf(0f, 1f - z / Z_FAR)
            val (pcx, pcy) = path(time - sp.z * 0.5f)
            curBuf[bi++] = (pcx + sp.ox) * fov / z + W / 2f    // sx
            curBuf[bi++] = (pcy + sp.oy) * fov / z + H / 2f    // sy
            curBuf[bi++] = maxOf(2f, fov / z * 0.05f)           // r
            curBuf[bi++] = (sp.hue + nearT * 0.35f) % 1f        // h
            curBuf[bi++] = 0.35f + nearT * 0.60f                // bright
        }
        sparkHist[sparkHistHead]     = curBuf
        sparkHistSize[sparkHistHead] = curCount
        sparkHistHead = (sparkHistHead + 1) % SPARK_HIST_LEN

        // ── Draw spark ring buffer (oldest→newest) + current frame, additive ──
        // Simulates psysuals _SPARK_FADE=10: ~25-frame persistence on spark_surf.
        draw.setAdditiveBlend()
        for (age in SPARK_HIST_LEN - 1 downTo 0) {
            val slotIdx = (sparkHistHead - 1 - age + SPARK_HIST_LEN * 2) % SPARK_HIST_LEN
            val buf     = sparkHist[slotIdx] ?: continue
            val cnt     = sparkHistSize[slotIdx]
            if (cnt == 0) continue
            // alpha decays linearly: oldest frame gets ~10/255, newest gets full
            val ageFrac = age.toFloat() / SPARK_HIST_LEN.toFloat()
            val aMul    = 1f - ageFrac   // 0 (oldest) → 1 (current)
            var p = 0
            repeat(cnt) {
                val sx     = buf[p++]; val sy     = buf[p++]
                val r      = buf[p++]; val h      = buf[p++]; val bright = buf[p++]
                val hc = GLDraw.hsl(h, l = bright * 0.25f)
                draw.circle(sx, sy, r + 4f, hc[0], hc[1], hc[2], 0.6f * aMul, filled = true, segments = 10)
                val bc = GLDraw.hsl(h, l = bright)
                draw.circle(sx, sy, maxOf(1f, r), bc[0], bc[1], bc[2], aMul, filled = true, segments = 10)
            }
        }
        draw.setNormalBlend()
    }
}
