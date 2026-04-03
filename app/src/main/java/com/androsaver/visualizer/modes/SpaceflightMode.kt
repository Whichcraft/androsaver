package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Spaceflight — psychedelic hyperspace trip.
 * Rainbow warp streaks burst from the ship's focal point; beat fires neon rings;
 * nebula planets drift toward the camera; ship banks through with twin engine blooms.
 * Port of psysuals Spaceflight (v2.1.0).
 */
class SpaceflightMode : BaseMode() {

    override val name = "Spaceflight"

    private companion object {
        const val N_STARS = 340
        const val TAU     = (Math.PI * 2).toFloat()
        const val PI_F    = Math.PI.toFloat()
        val SHIP_PTS = arrayOf(
             0f to -64f,
            -44f to 24f, -20f to 8f, -14f to 36f,
             14f to 36f,  20f to 8f,  44f to 24f
        )
        val ENGINE_L = -14f to 36f
        val ENGINE_R =  14f to 36f
    }

    // Stars: [angle, depth, speedFrac, ownHue]
    private val stars = Array(N_STARS) {
        floatArrayOf(
            (Math.random() * TAU).toFloat(),
            Math.random().toFloat(),
            0.4f + Math.random().toFloat() * 1.2f,
            Math.random().toFloat()
        )
    }

    // Beat rings: [x, y, radius, hue, age, maxAge]
    private val rings = ArrayList<FloatArray>(40)

    private data class Planet(
        val angle: Float, val radial: Float,
        val depth: Float, val dd: Float,
        val baseR: Float, val hue: Float,
        val hasRings: Boolean, val ringHue: Float
    )
    private val planets = ArrayList<Planet>(4)
    private var planetCd = 0

    // Ship
    private var shipX = 0f; private var shipY = 0f; private var shipRoll = 0f
    private var focalX = 0f; private var focalY = 0f

    // Maneuver
    private enum class Man { JINK_L, JINK_R, CLIMB_D, CLIMB_U, BARREL }
    private var man: Man? = null
    private var manT = 0; private var manDur = 0; private var manCd = 0

    private var hue = 0f
    private var t = 0
    private var speed = 1f

    override fun reset() {
        hue = Math.random().toFloat(); t = 0; speed = 1f
        rings.clear(); planets.clear()
        planetCd = (150 + Math.random() * 200).toInt()
        manCd = (100 + Math.random() * 150).toInt()
        man = null; manT = 0
        for (s in stars) {
            s[0] = (Math.random() * TAU).toFloat()
            s[1] = Math.random().toFloat()
            s[2] = 0.4f + Math.random().toFloat() * 1.2f
            s[3] = Math.random().toFloat()
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(20f / 255f)

        val beat = audio.beat
        val bass = audio.fft.meanSlice(0, 6)
        hue = (hue + 0.005f) % 1f
        t++
        speed += (1f + bass * 3.8f + beat * 5.5f - speed) * 0.10f

        val cx = draw.W / 2f; val cy = draw.H / 2f

        // ── Maneuver ────────────────────────────────────────────────────────
        if (man != null) {
            manT++
            val tn = manT.toFloat() / manDur
            if (tn >= 1f) {
                man = null; manCd = (80 + Math.random() * 120).toInt()
                shipX = cx; shipY = cy; shipRoll = 0f
            } else {
                when (man) {
                    Man.JINK_L  -> { shipX = cx - sin(tn * PI_F) * 200f; shipY = cy;                               shipRoll = -sin(tn * PI_F) * 0.65f }
                    Man.JINK_R  -> { shipX = cx + sin(tn * PI_F) * 200f; shipY = cy;                               shipRoll =  sin(tn * PI_F) * 0.65f }
                    Man.CLIMB_D -> { shipX = cx;                           shipY = cy - sin(tn * PI_F) * 150f;      shipRoll = -sin(tn * PI_F) * 0.30f }
                    Man.CLIMB_U -> { shipX = cx;                           shipY = cy + sin(tn * PI_F) * 150f;      shipRoll =  sin(tn * PI_F) * 0.30f }
                    Man.BARREL  -> { shipX = cx + sin(tn * TAU) * 150f;   shipY = cy + cos(tn * TAU) * 110f - 110f; shipRoll = tn * TAU }
                    else -> {}
                }
            }
        } else {
            manCd--
            if (manCd <= 0) {
                val all = Man.values()
                man = all[(Math.random() * all.size).toInt()]
                manT = 0
                manDur = when (man) {
                    Man.JINK_L, Man.JINK_R   -> 160
                    Man.CLIMB_D, Man.CLIMB_U -> 140
                    Man.BARREL               -> 230
                    else                     -> 160
                }
            }
            shipX = cx + sin(t * 0.018f) * 20f
            shipY = cy + sin(t * 0.011f) * 14f
            shipRoll = sin(t * 0.018f) * 0.14f
        }
        focalX += (shipX - focalX) * 0.08f
        focalY += (shipY - focalY) * 0.08f
        val fx = focalX; val fy = focalY

        val ddx = fx - shipX; val ddy = fy - shipY
        val shipHeading = if (sqrt(ddx * ddx + ddy * ddy) > 6f) atan2(ddy, ddx) else -PI_F / 2f

        // ── Beat rings ───────────────────────────────────────────────────────
        if (beat > 0.5f) {
            repeat(1 + (beat * 1.5f).toInt()) {
                val rh = (hue + Math.random().toFloat() * 0.6f) % 1f
                rings.add(floatArrayOf(fx, fy, 8f, rh, 0f, (30 + beat * 18).toFloat()))
            }
        }
        val keepRings = ArrayList<FloatArray>()
        for (ring in rings) {
            ring[2] += 5f + speed * 1.8f; ring[4] += 1f
            val frac = ring[4] / ring[5]
            if (frac < 1f) {
                keepRings.add(ring)
                val c = GLDraw.hsl(ring[3], l = 0.85f - frac * 0.55f)
                draw.circle(ring[0], ring[1], ring[2].coerceAtLeast(1f),
                    c[0], c[1], c[2], 0.9f, filled = false, segments = 40)
            }
        }
        rings.clear()
        rings.addAll(if (keepRings.size > 40) keepRings.takeLast(40) else keepRings)

        // ── Rainbow warp streaks ─────────────────────────────────────────────
        val maxR = sqrt((draw.W * draw.W + draw.H * draw.H).toFloat()) * 0.80f
        for (st in stars) {
            val oldD = st[1]
            st[1] += 0.0024f * st[2] * speed
            val c = GLDraw.hsl((st[3] + st[1] * 0.45f + hue) % 1f,
                               l = minOf(0.98f, 0.28f + st[1] * 0.68f + beat * 0.12f))
            draw.line(fx + cos(st[0]) * oldD * maxR, fy + sin(st[0]) * oldD * maxR,
                      fx + cos(st[0]) * st[1]  * maxR, fy + sin(st[0]) * st[1]  * maxR,
                      c[0], c[1], c[2], 1f)
            if (st[1] >= 1f) {
                st[0] = (Math.random() * TAU).toFloat()
                st[1] = Math.random().toFloat() * 0.06f
                st[2] = 0.4f + Math.random().toFloat() * 1.2f
                st[3] = Math.random().toFloat()
            }
        }

        // ── Planets ──────────────────────────────────────────────────────────
        planetCd--
        if (planetCd <= 0 && planets.size < 4) {
            planets.add(Planet(
                angle    = (Math.random() * TAU).toFloat(),
                radial   = 0.25f + Math.random().toFloat() * 0.57f,
                depth    = 0.03f,
                dd       = 0.0014f + Math.random().toFloat() * 0.0018f,
                baseR    = 32f + Math.random().toFloat() * 63f,
                hue      = Math.random().toFloat(),
                hasRings = Math.random() > 0.45,
                ringHue  = Math.random().toFloat()
            ))
            planetCd = (260 + Math.random() * 340).toInt()
        }
        val hypot = sqrt((draw.W * draw.W + draw.H * draw.H).toFloat())
        val keepPlanets = ArrayList<Planet>()
        for (p in planets) {
            val np = p.copy(depth = p.depth + p.dd * speed * 0.28f)
            if (np.depth < 1.15f) { keepPlanets.add(np); drawPlanet(draw, np, fx, fy, hypot) }
        }
        planets.clear(); planets.addAll(keepPlanets)

        // ── Ship ─────────────────────────────────────────────────────────────
        drawShip(draw, shipX, shipY, shipHeading, shipRoll, beat)
    }

    private fun drawPlanet(draw: GLDraw, p: Planet, fx: Float, fy: Float, hypot: Float) {
        val rD = p.depth * hypot * 0.75f * p.radial
        val px = fx + cos(p.angle) * rD
        val py = fy + sin(p.angle) * rD
        val r = maxOf(2f, p.baseR * p.depth)
        GLDraw.hsl(p.hue, l = 0.18f).also { c -> draw.circle(px, py, r,        c[0], c[1], c[2], 1f, segments = 32) }
        if (r > 5f) {
            GLDraw.hsl(p.hue, l = 0.34f).also { c -> draw.circle(px, py, r * 0.80f, c[0], c[1], c[2], 1f, segments = 28) }
            GLDraw.hsl(p.hue, l = 0.52f).also { c -> draw.circle(px, py, r * 0.56f, c[0], c[1], c[2], 1f, segments = 24) }
            GLDraw.hsl(p.hue, l = 0.72f).also { c -> draw.circle(px - r / 3f, py - r / 3f, r / 4f, c[0], c[1], c[2], 1f, segments = 16) }
        }
        GLDraw.hsl((p.hue + 0.12f) % 1f, l = 0.68f).also { c ->
            draw.circle(px, py, r, c[0], c[1], c[2], 0.9f, filled = false, segments = 32)
        }
        if (p.hasRings && r > 10f) {
            GLDraw.hsl(p.ringHue, l = 0.62f).also { c ->
                draw.circle(px, py, r * 1.85f, c[0], c[1], c[2], 0.7f, filled = false, segments = 40)
            }
        }
    }

    private fun rotPts(pts: Array<Pair<Float, Float>>, x: Float, y: Float, ca: Float, sa: Float): FloatArray {
        val buf = FloatArray(pts.size * 2)
        for (i in pts.indices) {
            buf[i * 2]     = x + pts[i].first * ca - pts[i].second * sa
            buf[i * 2 + 1] = y + pts[i].first * sa + pts[i].second * ca
        }
        return buf
    }

    private fun drawShip(draw: GLDraw, x: Float, y: Float, heading: Float, roll: Float, beat: Float) {
        val ang = heading + roll
        val ca = cos(ang); val sa = sin(ang)
        val gr = maxOf(14f, 22f + beat * 44f)
        val eh = (hue + 0.55f) % 1f
        for ((eox, eoy) in listOf(ENGINE_L, ENGINE_R)) {
            val ex = x + eox * ca - eoy * sa
            val ey = y + eox * sa + eoy * ca
            GLDraw.hsl(eh, l = 0.42f).also { c -> draw.circle(ex, ey, gr,                  c[0], c[1], c[2], 0.37f, segments = 20) }
            GLDraw.hsl(eh, l = 0.95f).also { c -> draw.circle(ex, ey, maxOf(4f, gr / 2f),  c[0], c[1], c[2], 0.90f, segments = 16) }
            GLDraw.hsl(eh, l = 0.97f).also { c -> draw.circle(ex, ey, maxOf(5f, gr / 3f),  c[0], c[1], c[2], 1f,    segments = 12) }
        }
        rotPts(SHIP_PTS, x, y, ca, sa).let { hull ->
            GLDraw.hsl((hue + 0.12f) % 1f, l = 0.30f).also { c -> draw.polygon(hull, c[0], c[1], c[2], 1f, filled = true)  }
            GLDraw.hsl(hue,                 l = 0.82f).also { c -> draw.polygon(hull, c[0], c[1], c[2], 1f, filled = false) }
        }
        rotPts(arrayOf(ENGINE_L.first to ENGINE_L.second - 5f,
                       ENGINE_R.first to ENGINE_R.second - 5f,
                       ENGINE_R.first to ENGINE_R.second + 3f,
                       ENGINE_L.first to ENGINE_L.second + 3f), x, y, ca, sa).let { panel ->
            GLDraw.hsl((hue + 0.55f) % 1f, l = 0.28f).also { c -> draw.polygon(panel, c[0], c[1], c[2], 1f, filled = true) }
        }
    }
}
