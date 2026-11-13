package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Mycelium — a violet hyphal web that carries travelling light packets.
 *
 * Three fruiting bodies grow branching filaments over an opaque indigo
 * ground. Cyan packets travel along the filaments and hand over to a
 * neighbouring filament at the junctions; amber spores drift, settle and
 * germinate into new growth. The colony keeps a slow heartbeat of its own, so
 * light travels even in silence.
 */
class MyceliumMode : BaseMode() {

    override val name = "Mycelium"

    private companion object {
        const val GROUND_R = 9f / 255f
        const val GROUND_G = 7f / 255f
        const val GROUND_B = 24f / 255f
        const val PULSE_HUE = MyceliumColony.PULSE_HUE
        const val AMBER_HUE = MyceliumColony.AMBER_HUE
    }

    private var colony: MyceliumColony? = null
    private var lastW = 0
    private var lastH = 0
    private var bassPrev = 0f
    private val edgeLight = FloatArray(MyceliumColony.MAX_EDGES)
    private val glowColor = FloatArray(4)
    private val coreColor = FloatArray(4)

    override fun reset() {
        bassPrev = 0f
        colony = null
        lastW = 0
        lastH = 0
    }

    override fun onSurfaceCreated() {
        super.onSurfaceCreated()
        colony = null
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val model = colony
        if (model == null) {
            colony = MyceliumColony(draw.W, draw.H)
            lastW = draw.W
            lastH = draw.H
        } else if (lastW != draw.W || lastH != draw.H) {
            // A resize re-lays out the network; it never rebuilds it.
            model.resize(draw.W, draw.H)
            lastW = draw.W
            lastH = draw.H
        }
        val colony = this.colony ?: return

        val response = prepare(draw, audio)
        val step = response.step
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = response.motion[1].coerceIn(0f, 1.5f)
        val treble = response.motion[2].coerceIn(0f, 1.5f)
        val lightBass = response.light[0]
        val onset = bassPrev < 0.5f && lightBass >= 0.5f
        bassPrev = lightBass

        colony.update(step, bass, mid, treble, lightBass, onset, false)

        // The ground is opaque, so the colony persists as state rather than as
        // decaying pixels.
        draw.setNormalBlend()
        draw.rect(0f, 0f, draw.W.toFloat(), draw.H.toFloat(), GROUND_R, GROUND_G, GROUND_B, 1f)

        draw.setAdditiveBlend()
        drawBodies(draw, colony, bass, draw.W, draw.H)
        drawFilaments(draw, colony, draw.W, draw.H)
        drawPulses(draw, colony)
        drawSpores(draw, colony)
        drawSparks(draw, colony, treble)
        drawRings(draw, colony)
        draw.setNormalBlend()
    }

    /** A soft radial glow around every fruiting body. */
    private fun drawBodies(draw: GLDraw, model: MyceliumColony, bass: Float, width: Int, height: Int) {
        val radius = maxOf(8f, 0.22f * minOf(width, height))
        for (index in 0 until model.bodyCount) {
            val x = model.bodyX(index)
            val y = model.bodyY(index)
            val hue = model.bodyHue(index)
            GLDraw.hsl(hue, 0.75f, 0.42f, 1f, glowColor)
            for (ring in 0 until 4) {
                val r = radius * (1f - ring * 0.22f)
                val alpha = 0.05f * (1f - ring * 0.2f)
                draw.circle(x, y, r, glowColor[0], glowColor[1], glowColor[2], alpha, filled = true, segments = 20)
            }
            GLDraw.hsl(hue, 0.80f, 0.75f, 1f, coreColor)
            draw.circle(x, y, maxOf(1f, 5f + bass * 5f), coreColor[0], coreColor[1], coreColor[2], 0.9f, filled = true, segments = 16)
            GLDraw.hsl(hue, 0.60f, 0.45f, 1f, glowColor)
            draw.circle(x, y, maxOf(1f, 16f + bass * 22f), glowColor[0], glowColor[1], glowColor[2], 0.7f, filled = false, segments = 24)
        }
    }

    /** The filaments: a tapered glow line and a brighter core line. */
    private fun drawFilaments(draw: GLDraw, model: MyceliumColony, width: Int, height: Int) {
        for (index in edgeLight.indices) edgeLight[index] = 0f
        for (i in 0 until model.pulseCount) {
            val edge = model.pulseEdge(i)
            if (edge in edgeLight.indices) {
                edgeLight[edge] = minOf(1f, edgeLight[edge] + model.pulseEnergy(i))
            }
        }
        val span = maxOf(1f, 1.3f * minOf(width, height))
        for (index in 0 until MyceliumColony.MAX_EDGES) {
            if (!model.edgeAlive(index)) continue
            val a = model.edgeStart(index)
            val b = model.edgeEnd(index)
            val fade = maxOf(0.25f, 1f - model.edgeAgeOf(index) / model.edgeLifetimeOf(index))
            val lit = edgeLight[index]
            val hue = model.edgeHueOf(index)
            val coreHue = hue + (PULSE_HUE - hue) * 0.45f * lit
            val glowLight = minOf(1f, 0.18f + 0.12f * fade + 0.30f * lit)
            val coreLight = minOf(1f, 0.55f + 0.30f * fade + 0.45f * lit)
            val taper = taper(model, index, span, width, height)
            val depth = model.edgeDepthOf(index)
            val glowWidth = maxOf(1f, (5f - depth / 2) * taper)
            val coreWidth = maxOf(1f, (2f - depth / 3) * taper)
            val ax = model.nodePosX(a)
            val ay = model.nodePosY(a)
            val bx = model.nodePosX(b)
            val by = model.nodePosY(b)
            GLDraw.hsl(hue, 0.72f, glowLight, 1f, glowColor)
            draw.antiAliasedLine(ax, ay, bx, by, glowColor[0], glowColor[1], glowColor[2], 0.55f, glowWidth)
            GLDraw.hsl(coreHue, 0.85f, coreLight, 1f, coreColor)
            draw.antiAliasedLine(ax, ay, bx, by, coreColor[0], coreColor[1], coreColor[2], 0.95f, coreWidth)
        }
    }

    /** Filaments are thick near their body and fine at the frontier. */
    private fun taper(model: MyceliumColony, edge: Int, span: Float, width: Int, height: Int): Float {
        val a = model.edgeStart(edge)
        val b = model.edgeEnd(edge)
        val bodyIndex = model.edgeBodyOf(edge)
        val bodyX: Float
        val bodyY: Float
        if (bodyIndex in 0 until model.bodyCount) {
            bodyX = model.bodyX(bodyIndex)
            bodyY = model.bodyY(bodyIndex)
        } else {
            bodyX = width * 0.5f
            bodyY = height * 0.5f
        }
        val midX = (model.nodePosX(a) + model.nodePosX(b)) * 0.5f
        val midY = (model.nodePosY(a) + model.nodePosY(b)) * 0.5f
        val distance = hypot(midX - bodyX, midY - bodyY)
        return minOf(1f, maxOf(0.30f, 1f - distance / span))
    }

    /** The travelling packets and their tails. */
    private fun drawPulses(draw: GLDraw, model: MyceliumColony) {
        for (i in 0 until model.pulseCount) {
            val edge = model.pulseEdge(i)
            if (!model.edgeAlive(edge)) continue
            val a = model.edgeStart(edge)
            val b = model.edgeEnd(edge)
            val t = model.pulseT(i)
            val x = model.nodePosX(a) + (model.nodePosX(b) - model.nodePosX(a)) * t
            val y = model.nodePosY(a) + (model.nodePosY(b) - model.nodePosY(a)) * t
            val tailT = maxOf(0f, t - 0.18f)
            val tailX = model.nodePosX(a) + (model.nodePosX(b) - model.nodePosX(a)) * tailT
            val tailY = model.nodePosY(a) + (model.nodePosY(b) - model.nodePosY(a)) * tailT
            GLDraw.hsl(PULSE_HUE, 0.85f, 0.72f, 1f, coreColor)
            draw.antiAliasedLine(tailX, tailY, x, y, coreColor[0], coreColor[1], coreColor[2], 0.9f, 2f)
            draw.circle(x, y, maxOf(1f, 2f + model.pulseEnergy(i) * 3f),
                coreColor[0], coreColor[1], coreColor[2], 0.95f, filled = true, segments = 10)
        }
    }

    /** Spores are violet when released and amber when they settle. */
    private fun drawSpores(draw: GLDraw, model: MyceliumColony) {
        for (i in 0 until model.sporeCount) {
            val fade = maxOf(0f, model.sporeLife(i).toFloat() / maxOf(1, model.sporeMaxLife(i)))
            val violet = model.compositeHue(model.sporeHue(i))
            val hue = (violet + (1f - fade) * (((AMBER_HUE - violet) % 1f + 1f) % 1f)) % 1f
            GLDraw.hsl(hue, 0.55f, 0.45f * fade + 0.30f, 1f, coreColor)
            draw.circle(model.sporeX(i), model.sporeY(i), maxOf(1f, model.sporeSize(i) * fade),
                coreColor[0], coreColor[1], coreColor[2], 0.9f, filled = true, segments = 8)
        }
    }

    private fun drawSparks(draw: GLDraw, model: MyceliumColony, treble: Float) {
        if (model.sparkCount == 0) return
        GLDraw.hsl(AMBER_HUE, 0.85f, 0.55f + 0.25f * minOf(1f, treble), 1f, coreColor)
        val radius = maxOf(1f, 1f + treble * 2f)
        for (i in 0 until model.sparkCount) {
            draw.circle(model.sparkX(i), model.sparkY(i), radius,
                coreColor[0], coreColor[1], coreColor[2], 0.95f, filled = true, segments = 6)
        }
    }

    private fun drawRings(draw: GLDraw, model: MyceliumColony) {
        for (i in 0 until model.ringCount) {
            GLDraw.hsl(model.compositeHue(0.10f), 0.70f, 0.15f + 0.35f * model.ringAlpha(i), 1f, glowColor)
            draw.circle(model.ringX(i), model.ringY(i), maxOf(1f, model.ringRadius(i)),
                glowColor[0], glowColor[1], glowColor[2], 0.8f, filled = false, segments = 24)
        }
    }
}
