package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** psysuals 3.20 stable 3:2:5 knot with 60 Hz sampled trails, smooth rotation/scale and no bridges across discontinuities. */
class LissajousMode : BaseMode() {

    override val name = "Lissajous"

    private companion object {
        const val TRAIL = 900
        const val N_SYM = 3
        const val TAU   = (Math.PI * 2).toFloat()
    }

    private val histX = FloatArray(TRAIL)
    private val histY = FloatArray(TRAIL)
    private val histZ = FloatArray(TRAIL)
    private var histSize = 0
    private var histHead = 0
    private var hue  = 0f
    private var t    = 0f
    private var rx   = 0f; private var ry   = 0f
    private var rvx  = 0.006f; private var rvy = 0.009f
    private var dx   = 0f; private var dz   = PI.toFloat() / 4f
    private val dy   = PI.toFloat() / 2f
    private var scale = 1f; private var svel = 0f
    private var sampleCredit=0f

    override fun reset() {
        histSize = 0;sampleCredit=0f
        histHead = 0
        hue = 0f; t = 0f; rx = 0f; ry = 0f
        rvx = 0.006f; rvy = 0.009f
        dx = 0f; dz = PI.toFloat() / 4f
        scale = 1f; svel = 0f
    }

    // Reusable flat buffer: [rawX0,rawY0, rawX1,rawY1, ...] — avoids per-frame allocation
    private var rawBuf = FloatArray(TRAIL * 2)
    private val ptsBuf = FloatArray(TRAIL * 2)
    private val colorsBuf = FloatArray(TRAIL * 4)
    private val color = FloatArray(4)

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val response=prepare(draw,audio);val step=response.step
        draw.fadeBlackTimed(.18f,step)
        val bass=response.motion[0];val mid=response.motion[1];val high=response.motion[2]
        val clampedBeat=response.light[0]*response.accent
        hue+=(.006f+response.light[0]*.02f)*step
        dx+=(.0003f+bass*.0002f)*step;dz+=(.0002f+high*.0002f)*step
        val speed=(.010f+bass*.004f+mid*.002f)*response.motionScale
        val previousT=t;t+=speed*step
        val count=(sampleCredit+step+1e-6f).toInt();val first=1f-sampleCredit
        repeat(count){ i -> val st=previousT+speed*(first+i)
            histX[histHead]=sin(3f*st+dx);histY[histHead]=sin(2f*st+dy);histZ[histHead]=sin(5f*st+dz)
            histHead=(histHead+1)%TRAIL;histSize=minOf(TRAIL,histSize+1)
        }
        sampleCredit=maxOf(0f,sampleCredit+step-count)
        scale=response.smooth(scale,1f+bass*.18f,.08f)
        rvx=response.smooth(rvx,.006f+bass*.008f+mid*.003f,.06f)
        rvy=response.smooth(rvy,.009f+bass*.010f+mid*.004f,.06f)
        rx+=rvx*step*response.motionScale;ry+=rvy*step*response.motionScale

        val n = histSize
        if (n < 2) return

        // Precompute rotation elements once (avoids 1400 Triple+Pair allocations per frame)
        val s    = scale
        val cxR  = cos(rx);  val sxR = sin(rx)
        val cyR  = cos(ry);  val syR = sin(ry)
        val fovL = minOf(draw.W, draw.H) * 0.52f
        if (rawBuf.size < n * 2) rawBuf = FloatArray(n * 2)
        val raw = rawBuf
        for (i in 0 until n) {
            val historyIndex = (histHead - n + i + TRAIL) % TRAIL
            val px = histX[historyIndex]
            val py = histY[historyIndex]
            val pz = histZ[historyIndex]
            val x   = px * s;  val y  = py * s;  val z  = pz * s
            val y2  = y * cxR  - z  * sxR
            val z2  = y * sxR  + z  * cxR
            val x3  = x * cyR  + z2 * syR
            val z3  = -x * syR + z2 * cyR
            val zcam = maxOf(z3 + 3.4f, 0.75f)
            raw[i * 2]     = x3 * fovL / zcam
            raw[i * 2 + 1] = y2 * fovL / zcam
        }

        val cx = draw.W / 2f; val cy = draw.H / 2f

        val l1Bright = minOf(0.90f + clampedBeat * 0.08f + high * 0.14f, 0.98f)

        for (sym in 0 until N_SYM) {
            val ang = sym.toFloat() / N_SYM * TAU
            val ca = cos(ang); val sa = sin(ang)

            for (j in 0 until n) {
                val px = raw[j * 2]; val py = raw[j * 2 + 1]
                ptsBuf[j * 2]     = cx + px * ca - py * sa
                ptsBuf[j * 2 + 1] = cy + px * sa + py * ca
            }

            for (pass in 0..1) {
                val lTail = if (pass == 0) 0.08f else 0.50f
                val lHead = if (pass == 0) 0.22f else l1Bright
                for (j in 0 until n) {
                    val tFrac = j.toFloat() / n
                    val h = (hue + sym.toFloat() / N_SYM * 0.33f + tFrac * 0.55f) % 1f
                    val l = lTail + tFrac * (lHead - lTail)
                    GLDraw.hsl(h, 1f, l, 1f, color)
                    colorsBuf[j * 4] = color[0]
                    colorsBuf[j * 4 + 1] = color[1]
                    colorsBuf[j * 4 + 2] = color[2]
                    colorsBuf[j * 4 + 3] = if (lTail < 0.15f) 0.30f else 1f
                }
                for(j in 1 until n) {
                    val a=(j-1)*2;val b=j*2
                    if(hypot(ptsBuf[b]-ptsBuf[a],ptsBuf[b+1]-ptsBuf[a+1])<=minOf(draw.W,draw.H)*.10f)
                        draw.line(ptsBuf[a],ptsBuf[a+1],ptsBuf[b],ptsBuf[b+1],colorsBuf[j*4],colorsBuf[j*4+1],colorsBuf[j*4+2],colorsBuf[j*4+3])
                }
            }

            // Head dot at the tip of each arm (treble scales knot size and triggers outer ring)
            val hpx = ptsBuf[(n - 1) * 2]; val hpy = ptsBuf[(n - 1) * 2 + 1]
            val r   = maxOf(3f, 7f + clampedBeat * 5.0f + high * 3.0f)
            GLDraw.hsl((hue + sym.toFloat() / N_SYM * 0.33f) % 1f, 1f, 0.88f, 1f, color)
            draw.circle(hpx, hpy, r,       color[0], color[1], color[2], 1f, segments = 16)
            draw.circle(hpx, hpy, r / 3f,  1f,   1f,   1f,   1f, segments = 12)
            if (clampedBeat > 0.3f || high > 0.4f) {
                GLDraw.hsl((hue + sym.toFloat() / N_SYM * 0.33f + 0.5f) % 1f, 1f, 0.45f + clampedBeat * 0.25f + high * 0.15f, 1f, color)
                draw.circle(hpx, hpy, r * (1.5f + high * 0.8f), color[0], color[1], color[2], 0.5f, filled = false, segments = 16)
            }
        }

    }
}
