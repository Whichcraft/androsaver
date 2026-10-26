package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** psysuals 3.20 interior spawning, center-directed edge avoidance, bounded pair orbits and retained sprite state on resize. Android retains its paired-agent implementation. */
class ButterfliesMode : BaseMode() {

    override val name = "Butterflies"

    private companion object {
        const val TAU        = (Math.PI * 2).toFloat()
        const val PI_F       = Math.PI.toFloat()
        const val MAX_PAIRS  = 6
        const val SOLO_SCALE = 5.04f  // 7.2 * 0.70
        const val LOVE_SCALE = 4.79f  // 6.84 * 0.70
    }

    private var screenW = 1920
    private var screenH = 1080
    private val rnd = java.util.Random()

    // ── Wing polygon ─────────────────────────────────────────────────────────

    private fun wingPoly(
        x: Float, y: Float, heading: Float,
        side: Float, upper: Float, flap: Float, scale: Float
    ): FloatArray {
        val ca = cos(heading); val sa = sin(heading)
        val off = 5f * scale * upper
        val ax = x + ca * off; val ay = y + sa * off
        val ws = scale * (if (upper == 1f) 22f else 13f)
        val spread = (0.22f + flap * 0.68f) * PI_F / 2f
        val wAng = heading + side * spread + upper * 0.10f
        val tipX = ax + cos(wAng) * ws;         val tipY = ay + sin(wAng) * ws
        val feAng = wAng - side * PI_F * 0.28f
        val feX = ax + cos(feAng) * ws * 0.58f; val feY = ay + sin(feAng) * ws * 0.58f
        val reAng = wAng + side * PI_F * 0.32f
        val reX = ax + cos(reAng) * ws * 0.52f; val reY = ay + sin(reAng) * ws * 0.52f
        return floatArrayOf(ax, ay, feX, feY, tipX, tipY, reX, reY)
    }

    // ── Single butterfly ─────────────────────────────────────────────────────

    private inner class Butterfly(
        var x: Float, var y: Float,
        var hue: Float,
        var scale: Float
    ) {
        var heading   = Math.random().toFloat() * TAU
        var wingPhase = Math.random().toFloat() * TAU
        var departAng: Float? = null
        private var wanderDes = heading
        private var wanderCd  = 0

        val offScreen: Boolean get() {
            val m = 80f * scale
            return x < -m || x > screenW + m || y < -m || y > screenH + m
        }

        fun startDepart() { if (departAng == null) departAng = Math.random().toFloat() * TAU }

        fun update(bass: Float, beat: Float, chasePos: Pair<Float, Float>? = null) {
            wingPhase += 0.09f + bass * 0.16f + beat * 0.06f

            if (departAng != null) {
                val da = departAng!!
                heading += ((da - heading + PI_F) % TAU - PI_F)
                val spd = (2.5f + bass * 0.5f) * scale
                x += cos(heading) * spd
                y += sin(heading) * spd
                return
            }

            val desired = if (chasePos != null) {
                atan2(chasePos.second - y, chasePos.first - x)
            } else {
                wanderCd--
                if (wanderCd <= 0) {
                    wanderDes = heading + Math.random().toFloat() * PI_F * 1.2f - PI_F * 0.6f
                    wanderCd  = (60 + Math.random() * 120).toInt()
                }
                wanderDes
            }

            val margin=minOf(screenW,screenH)*.13f
            val nearEdge=x<margin || x>screenW-margin || y<margin || y>screenH-margin
            val target=if(nearEdge)atan2(screenH*.5f-y,screenW*.5f-x) else desired
            val delta=atan2(sin(target-heading),cos(target-heading))
            heading+=(delta*(if(chasePos==null).12f else .22f)).coerceIn(-.18f,.18f)
            val speed=(1.1f+bass*.9f+beat*.5f)*scale/4.8f
            x=(x+cos(heading)*speed).coerceIn(margin*.5f,screenW-margin*.5f)
            y=(y+sin(heading)*speed).coerceIn(margin*.5f,screenH-margin*.5f)
        }

        fun draw(draw: GLDraw, outlineHue: Float) {
            val flap = sin(wingPhase) * 0.5f + 0.5f
            val wc1  = GLDraw.hsl(hue, l = 0.58f)
            val wc2  = GLDraw.hsl((hue + 0.10f) % 1f, l = 0.46f)
            val oc   = GLDraw.hsl(outlineHue, l = 0.20f)
            for (upper in floatArrayOf(-1f, 1f)) {
                val wc = if (upper == 1f) wc1 else wc2
                for (side in floatArrayOf(-1f, 1f)) {
                    val pts = wingPoly(x, y, heading, side, upper, flap, scale)
                    draw.polygon(pts, wc[0], wc[1], wc[2], 1f, filled = true)
                    if (scale > 3.0f) {
                        draw.polygon(pts, oc[0], oc[1], oc[2], 0.8f, filled = false)
                    }
                }
            }
            val ca = cos(heading); val sa = sin(heading)
            val bl = 8f * scale
            val hx = x + ca * bl; val hy = y + sa * bl
            val tx = x - ca * bl; val ty = y - sa * bl
            val bc = GLDraw.hsl(hue, l = 0.28f)
            draw.line(hx, hy, tx, ty, bc[0], bc[1], bc[2], 1f)
        }
    }

    // ── Pair ─────────────────────────────────────────────────────────────────

    private inner class ButterflyPair(spawnDelay: Int) {
        private val joinDelay = (600 + Math.random() * 1200).toInt()
        private val lifetime  = (2400 + Math.random() * 3000).toInt()
        private var age       = -spawnDelay
        private var orbitAng  = (Math.random().toFloat() * TAU)
        var orbitR    = 240f
        var solo: Butterfly?  = null
        var love: Butterfly?  = null
        var departing = false
        private var breakCd    = (800 + Math.random() * 800).toInt()
        private var breakTimer = 0

        val dead: Boolean get() = departing &&
            (solo == null || solo!!.offScreen) &&
            (love == null || love!!.offScreen)

        fun update(bass: Float, beat: Float, globalHue: Float) {
            age++
            if (solo == null && age >= 0) {
                val (ex, ey) = edgeSpawn()
                solo = Butterfly(ex, ey, hue = globalHue, scale = maxOf(.35f,minOf(screenW,screenH)/360f*2.4f))
            }
            val sl = solo ?: return
            sl.hue = globalHue
            love?.hue = (globalHue + 0.50f) % 1f

            if (love == null && age >= joinDelay) {
                val (ex, ey) = edgeSpawn()
                love = Butterfly(ex, ey, hue = (globalHue + 0.50f) % 1f, scale = maxOf(.35f,minOf(screenW,screenH)/360f*2.4f))
            }
            if (age >= lifetime && !departing) {
                departing = true; sl.startDepart(); love?.startDepart()
            }

            val lv = love
            if (lv != null && !departing) {
                if (breakTimer > 0) {
                    breakTimer--
                } else {
                    breakCd--
                    if (breakCd <= 0) {
                        breakTimer = (200 + Math.random() * 300).toInt()
                        breakCd    = (900 + Math.random() * 900).toInt()
                        orbitR     = minOf(orbitR + 80f, 200f)
                    }
                }

                if (breakTimer > 0) {
                    sl.update(bass, beat)
                    lv.update(bass, beat)
                } else {
                    val angSpeed = 0.012f + beat * 0.020f + 0.003f * maxOf(0f, 1f - orbitR / 240f)
                    orbitAng += angSpeed
                    if (orbitR > 40f) {
                        orbitR -= 0.06f
                    }
                    orbitR=minOf(orbitR,minOf(screenW,screenH)*.15f)
                    val r=orbitR;val margin=r+minOf(screenW,screenH)*.15f
                    val cx=((sl.x+lv.x)*.5f).coerceIn(margin,screenW-margin)
                    val cy=((sl.y+lv.y)*.5f).coerceIn(margin,screenH-margin)
                    val soloTarget = Pair(cx+cos(orbitAng+PI_F)*r,cy+sin(orbitAng+PI_F)*r)
                    val loveTarget = Pair(cx+cos(orbitAng)*r,cy+sin(orbitAng)*r)
                    sl.update(bass, beat, chasePos = soloTarget)
                    lv.update(bass, beat, chasePos = loveTarget)
                }
            } else {
                sl.update(bass, beat)
                lv?.update(bass, beat)
            }

            if (lv != null) {
                val dist = hypot((lv.x - sl.x).toDouble(), (lv.y - sl.y).toDouble()).toFloat()
                val syncRange = 130f * maxOf(sl.scale, lv.scale)
                if (dist < syncRange) {
                    val sync = 1f - dist / syncRange
                    val diff = lv.wingPhase - sl.wingPhase
                    lv.wingPhase -= diff * sync * 0.12f
                }
            }
        }

        fun draw(draw: GLDraw, beat: Float, globalHue: Float) {
            val sl = solo ?: return
            val lv = love
            if (lv != null && beat > 0.8f && !departing) {
                val dist = hypot((lv.x - sl.x).toDouble(), (lv.y - sl.y).toDouble()).toFloat()
                if (dist < 300f) {
                    val mx = (sl.x + lv.x) / 2f; val my = (sl.y + lv.y) / 2f
                    val sc = GLDraw.hsl((globalHue + 0.12f) % 1f, l = 0.80f)
                    repeat(4) {
                        val sx = mx + rnd.nextGaussian().toFloat() * 25f
                        val sy = my + rnd.nextGaussian().toFloat() * 25f
                        val r  = 2f + Math.random().toFloat() * 3f
                        draw.circle(sx, sy, r, sc[0], sc[1], sc[2], 1f, segments = 8)
                    }
                }
            }
            sl.draw(draw, outlineHue = (globalHue + 0.05f) % 1f)
            lv?.draw(draw, outlineHue = (globalHue + 0.55f) % 1f)
        }
    }

    private fun edgeSpawn():Pair<Float,Float> {
        val margin=minOf(screenW,screenH)*.18f
        return margin+Math.random().toFloat()*(screenW-2f*margin) to margin+Math.random().toFloat()*(screenH-2f*margin)
    }

    // ── Main effect ──────────────────────────────────────────────────────────

    private val pairs = ArrayList<ButterflyPair>(MAX_PAIRS)
    private var globalHue = 0f

    override fun reset() {
        pairs.clear()
        globalHue = Math.random().toFloat()
        val offsets = IntArray(MAX_PAIRS) { i ->
            when (i) {
                0 -> 0
                1 -> (300 + Math.random() * 400).toInt()
                else -> (800 + Math.random() * 600).toInt()
            }
        }
        for (i in 0 until MAX_PAIRS) {
            pairs.add(ButterflyPair(spawnDelay = offsets[i]))
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(22f / 255f)
        if(screenW!=draw.W || screenH!=draw.H) {
            val sx=draw.W.toFloat()/screenW.coerceAtLeast(1);val sy=draw.H.toFloat()/screenH.coerceAtLeast(1)
            for(pair in pairs){for(butterfly in listOfNotNull(pair.solo,pair.love)){butterfly.x*=sx;butterfly.y*=sy;butterfly.scale*=minOf(sx,sy)};pair.orbitR*=minOf(sx,sy)}
        }
        screenW = draw.W; screenH = draw.H

        globalHue = (globalHue + 0.0014f) % 1f
        val beat = audio.beat
        val bass = beat

        pairs.removeAll { it.dead }
        while (pairs.size < MAX_PAIRS) {
            pairs.add(ButterflyPair(spawnDelay = (60 + Math.random() * 140).toInt()))
        }

        for ((i, pair) in pairs.withIndex()) {
            val gh = (globalHue + i.toFloat() / MAX_PAIRS) % 1f
            pair.update(bass, beat, gh)
        }

        for ((i, pair) in pairs.withIndex()) {
            val gh = (globalHue + i.toFloat() / MAX_PAIRS) % 1f
            pair.draw(draw, beat, gh)
        }
    }
}
