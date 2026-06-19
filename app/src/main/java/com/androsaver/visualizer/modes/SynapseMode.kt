package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Synapse — neural-network graph with cascading signal propagation.
 *
 * ~55 nodes in a loose ring layout, wired to nearest neighbours. When a node
 * fires it sends visible signal pulses down a subset of outgoing edges to
 * stabilize signal counts.
 *
 * Port of psysuals `effects/synapse.py` (v3.9.0).
 */
class SynapseMode : BaseMode() {

    override val name = "Synapse"

    private companion object {
        const val N_NODES        = 55
        const val EDGES_PER_NODE = 3
        const val MAX_SIGNALS    = 240
        val TAU = (2.0 * PI).toFloat()
    }

    private data class Signal(val edgeIdx: Int, var t: Float, val spd: Float, val hue: Float)

    private val nodeX  = FloatArray(N_NODES)
    private val nodeY  = FloatArray(N_NODES)
    private val edges  = ArrayList<IntArray>(N_NODES * EDGES_PER_NODE) // [from, to]
    private val outgoing = Array(N_NODES) { ArrayList<Int>() } // outgoing edge indices
    private val glow   = FloatArray(N_NODES)
    private val signals = ArrayList<Signal>()
    private var hue      = 0f
    private var beatPrev = 0f
    private var autoFireCd = 30
    private var initialized = false

    override fun reset() {
        edges.clear()
        signals.clear()
        for (i in 0 until N_NODES) {
            outgoing[i].clear()
        }
        glow.fill(0f)
        hue = 0f
        beatPrev = 0f
        autoFireCd = 30
        initialized = false
    }

    private fun init(W: Float, H: Float) {
        for (i in 0 until N_NODES) {
            val ang = Math.random().toFloat() * TAU
            val r   = (0.10f + Math.random().toFloat() * 0.34f) * minOf(W, H)
            nodeX[i] = W / 2f + cos(ang) * r * (0.80f + Math.random().toFloat() * 0.40f)
            nodeY[i] = H / 2f + sin(ang) * r * (0.60f + Math.random().toFloat() * 0.30f)
        }
        edges.clear()
        val edgeSet = HashSet<Pair<Int, Int>>()
        for (i in 0 until N_NODES) {
            val sorted = (0 until N_NODES)
                .filter { it != i }
                .sortedBy { hypot(nodeX[it] - nodeX[i], nodeY[it] - nodeY[i]) }
            for (k in 0 until EDGES_PER_NODE) {
                val toIdx = sorted[k]
                val pair = Pair(i, toIdx)
                if (!edgeSet.contains(pair)) {
                    edgeSet.add(pair)
                    edges.add(intArrayOf(i, toIdx))
                }
            }
        }
        for (i in 0 until N_NODES) {
            outgoing[i].clear()
        }
        for ((ei, e) in edges.withIndex()) {
            outgoing[e[0]].add(ei)
        }
        initialized = true
    }

    private fun fire(idx: Int, fanout: Int = 2) {
        glow[idx] = 1f
        val h = (hue + idx.toFloat() / N_NODES * 0.4f) % 1f
        val outList = outgoing[idx]
        if (outList.isEmpty() || signals.size >= MAX_SIGNALS) return
        val count = maxOf(1, minOf(fanout, outList.size))
        val shuffled = outList.shuffled().take(count)
        for (ei in shuffled) {
            if (signals.size >= MAX_SIGNALS) break
            val spd = 0.020f + Math.random().toFloat() * 0.010f
            signals.add(Signal(ei, 0f, spd, h))
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (!initialized) init(W, H)

        hue = (hue + 0.003f + mid * 0.002f) % 1f

        if (bass > 0.65f && beatPrev <= 0.65f) {
            val count = (1 + bass * 3).toInt()
            repeat(count) {
                fire((Math.random() * N_NODES).toInt().coerceIn(0, N_NODES - 1), fanout = 2 + (high * 2f).toInt())
            }
        }
        beatPrev = bass

        autoFireCd--
        if (autoFireCd <= 0) {
            fire((Math.random() * N_NODES).toInt().coerceIn(0, N_NODES - 1), fanout = 1)
            autoFireCd = maxOf(8, (25 - mid * 15).toInt())
        }

        val spdMul = 1f + bass * 2f
        val liveSigs = ArrayList<Signal>()
        val firedNodes = ArrayList<Int>()
        for (sig in signals) {
            sig.t += sig.spd * spdMul
            if (sig.t < 1f) {
                liveSigs.add(sig)
            } else {
                firedNodes.add(edges[sig.edgeIdx][1])
            }
        }
        signals.clear()
        signals.addAll(liveSigs)

        for (idx in firedNodes.take(18)) {
            fire(idx, fanout = 1 + (mid * 2f).toInt())
        }

        for (i in 0 until N_NODES) glow[i] = maxOf(0f, glow[i] - 0.035f)

        draw.fadeBlack(18f / 255f)

        // Draw edges (dim background wiring)
        for (e in edges) {
            val h   = (hue + e[0].toFloat() / N_NODES * 0.3f) % 1f
            val lum = 0.06f + glow[e[0]] * 0.14f
            val c   = GLDraw.hsl(h, l = lum)
            draw.line(nodeX[e[0]], nodeY[e[0]], nodeX[e[1]], nodeY[e[1]], c[0], c[1], c[2], c[3])
        }

        // Draw travelling signals
        draw.setAdditiveBlend()
        for ((ei, t, _, h) in signals) {
            val e  = edges[ei]
            val sx = nodeX[e[0]] + (nodeX[e[1]] - nodeX[e[0]]) * t
            val sy = nodeY[e[0]] + (nodeY[e[1]] - nodeY[e[0]]) * t
            val bright = 0.55f + bass * 0.25f + high * 0.15f
            val rPx    = maxOf(2f, 3f + bass * 2f)
            val cglow  = GLDraw.hsl(h, l = bright * 0.30f)
            draw.circle(sx, sy, rPx + 3f, cglow[0], cglow[1], cglow[2], cglow[3], filled = true, segments = 8)
            val c = GLDraw.hsl(h, l = bright)
            draw.circle(sx, sy, rPx, c[0], c[1], c[2], c[3], filled = true, segments = 8)
        }

        // Draw nodes
        for (i in 0 until N_NODES) {
            val g = glow[i]
            val h = (hue + i.toFloat() / N_NODES * 0.4f) % 1f
            if (g > 0.05f) {
                val rPx  = maxOf(2f, 4f + g * 8f + bass * 4f)
                val cg   = GLDraw.hsl(h, l = g * 0.22f)
                draw.circle(nodeX[i], nodeY[i], rPx + 3f, cg[0], cg[1], cg[2], cg[3], filled = true, segments = 8)
                val c = GLDraw.hsl(h, l = g * 0.70f)
                draw.circle(nodeX[i], nodeY[i], rPx, c[0], c[1], c[2], c[3], filled = true, segments = 8)
            } else {
                val c = GLDraw.hsl(h, l = 0.10f)
                draw.circle(nodeX[i], nodeY[i], 2f, c[0], c[1], c[2], c[3], filled = true, segments = 6)
            }
        }
        draw.setNormalBlend()
    }
}
