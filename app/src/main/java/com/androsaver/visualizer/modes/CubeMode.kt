package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.fftMean
import kotlin.math.*

/** Dual fitted wireframe cubes and independent satellites with psysuals 3.20 smoothed motion, restrained scale and separate light accents. Android retains bounded geometry trails. */
class CubeMode : BaseMode() {

    override val name = "Cube"

    // Unit cube vertices: 8 corners of [-1,1]^3
    private val vertsBase = arrayOf(
        floatArrayOf(-1f, -1f, -1f), floatArrayOf( 1f, -1f, -1f),
        floatArrayOf( 1f,  1f, -1f), floatArrayOf(-1f,  1f, -1f),
        floatArrayOf(-1f, -1f,  1f), floatArrayOf( 1f, -1f,  1f),
        floatArrayOf( 1f,  1f,  1f), floatArrayOf(-1f,  1f,  1f)
    )

    private val edges = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0,
        4 to 5, 5 to 6, 6 to 7, 7 to 4,
        0 to 4, 1 to 5, 2 to 6, 3 to 7
    )

    private var rx = 0f; private var ry = 0f; private var rz = 0f
    private var fadeHue = 0f
    private var scale = 1f; private var svel = 0f
    private var rvx = 0f; private var rvy = 0f; private var rvz = 0f
    private var orbAngle = 0f
    private var lastW=0;private var lastH=0;private var sampleCredit=0f

    // Independent satellite rotation (v2.0.0)
    private var satRx = 0f; private var satRy = 0f

    // Pre-allocated arrays to avoid GC pressure in hot path
    private val currentProj = Array(2) { FloatArray(16) }
    private val currentSatProj = Array(2) { FloatArray(16) }
    private val tempVerts3d = Array(8) { FloatArray(3) }

    // Main cube trail ring buffer
    private val TRAIL_LEN = 14
    private val trailProj  = Array(TRAIL_LEN) { Array(2) { FloatArray(16) } }
    private var trailHead  = 0
    private var trailCount = 0

    // Satellite trail: halved from 30→15 frames (v2.0.2: double _SAT_FADE → shorter persistence)
    // Always 2 sats (v2.0.0); ring buffer still used for per-frame replay
    private val SAT_TRAIL_LEN = 15
    private val satTrailProj  = Array(SAT_TRAIL_LEN) { Array(2) { FloatArray(16) } }
    private var satTrailHead  = 0
    private var satTrailCount = 0

    override fun reset() {
        rx = 0f; ry = 0f; rz = 0f
        fadeHue = 0f
        scale = 1f; svel = 0f
        rvx = 0f; rvy = 0f; rvz = 0f
        orbAngle = 0f;lastW=0;lastH=0;sampleCredit=0f
        satRx = 0f; satRy = 0f
        trailHead = 0; trailCount = 0
        satTrailHead = 0; satTrailCount = 0
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        // These geometry ring buffers already contain the intended trails.
        // Clear the retained FBO before replaying them so old frames cannot
        // accumulate into a permanent web of lines.
        draw.fadeBlack(1f)
        val response=prepare(draw,audio); val step=response.step
        if(lastW>0 && (lastW!=draw.W || lastH!=draw.H)) {
            val sx=draw.W.toFloat()/lastW;val sy=draw.H.toFloat()/lastH
            for(frames in listOf(trailProj,satTrailProj)) for(frame in frames) for(p in frame) for(i in 0..7){p[i*2]*=sx;p[i*2+1]*=sy}
        }
        lastW=draw.W;lastH=draw.H
        val samples=(sampleCredit+step+1e-6f).toInt();sampleCredit=maxOf(0f,sampleCredit+step-samples)
        val bass=response.motion[0]; val mid=response.motion[1]; val high=response.motion[2]
        val bassM=bass; val midM=mid; val highM=high
        val fov=minOf(draw.W,draw.H)*.9f
        fadeHue=(fadeHue+(.0012f+mid*.0005f)*step)%1f
        rvx=response.smooth(rvx,.004f+mid*.002f+bass*.003f,.04f)
        rvy=response.smooth(rvy,.005f+bass*.004f,.04f)
        rvz=response.smooth(rvz,.002f+high*.0015f,.04f)
        rx+=rvx*step*response.motionScale; ry+=rvy*step*response.motionScale; rz+=rvz*step*response.motionScale
        val previousScale=scale
        scale=response.smooth(scale,1f+bass*.06f,.035f); svel=scale-previousScale
        val jitterAmp=0f

        // ── Compute projections for main (ci=0) and inner (ci=1) cubes ────────
        val cubeScales = floatArrayOf(scale, scale * 0.45f)
        for (ci in 0..1) {
            val s = cubeScales[ci]
            for (vi in 0..7) {
                val jx = if (jitterAmp > 0.001f) (Math.random().toFloat() * 2f - 1f) * jitterAmp else 0f
                val jy = if (jitterAmp > 0.001f) (Math.random().toFloat() * 2f - 1f) * jitterAmp else 0f
                val jz = if (jitterAmp > 0.001f) (Math.random().toFloat() * 2f - 1f) * jitterAmp else 0f
                rotateVertex(vertsBase[vi][0] * s + jx, vertsBase[vi][1] * s + jy, vertsBase[vi][2] * s + jz, rx, ry, rz, tempVerts3d[vi])
                project(tempVerts3d[vi], draw.W, draw.H, fov, currentProj[ci], vi * 2)
            }
        }

        for (p in currentProj) fitViewport(p,draw.W,draw.H)

        // ── Update trail ring buffer ───────────────────────────────────────────
        repeat(samples) {
            for(ci in 0..1) System.arraycopy(currentProj[ci],0,trailProj[trailHead][ci],0,16)
            if(trailCount<TRAIL_LEN)trailCount++
            trailHead=(trailHead+1)%TRAIL_LEN
        }

        // ── Draw echo trail (oldest → newest-1, progressively brighter) ───────
        for (age in trailCount - 1 downTo 1) {
            val frameIdx = (trailHead - 1 - age + TRAIL_LEN * 2) % TRAIL_LEN
            val alpha    = (1f - age.toFloat() / trailCount.toFloat()) * 0.75f
            for (ci in 0..1) {
                val hueOff = if (ci == 1) 0.5f else 0f
                val proj   = trailProj[frameIdx][ci]
                for ((ei, edge) in edges.withIndex()) {
                    val (a, b) = edge
                    val h     = (fadeHue + hueOff + ei.toFloat() / edges.size * 0.4f) % 1f
                    val color = GLDraw.hsl(h, 1f, 0.32f)
                    draw.line(proj[a * 2], proj[a * 2 + 1], proj[b * 2], proj[b * 2 + 1],
                        color[0], color[1], color[2], alpha)
                }
            }
        }

        // ── Draw current frame (full brightness, treble adds line width) ─────────
        val lightness = minOf(.72f,.40f+response.light[0]*.12f*response.accent+response.light[2]*.04f)
        for (ci in 0..1) {
            val hueOff = if (ci == 1) 0.5f else 0f
            val proj   = currentProj[ci]
            for ((ei, edge) in edges.withIndex()) {
                val (a, b) = edge
                val h     = (fadeHue + hueOff + ei.toFloat() / edges.size * 0.4f) % 1f
                val color = GLDraw.hsl(h, 1f, lightness)
                draw.line(proj[a * 2], proj[a * 2 + 1], proj[b * 2], proj[b * 2 + 1],
                    color[0], color[1], color[2], 1f)
            }
        }

        // ── Orbiting satellite cubes (v2.0.0: always 2, independent rotation) ─
        val satScale = minOf(scale * 0.28f, 0.55f)
        val ORB_R    = 2.6f
        orbAngle += (.006f+bass*.002f+mid*.001f)*step*response.motionScale

        // Independent rotation with treble shimmer
        satRx += .008f*step*response.motionScale; satRy += .010f*step*response.motionScale

        // Compute current satellite projections (2 sats, 180° apart)
        for (si in 0..1) {
            val theta = orbAngle + si * PI.toFloat()   // always 180° apart
            val ox    = ORB_R * cos(theta)
            val oy    = ORB_R * sin(theta)
            for (vi in 0..7) {
                val jx = if (jitterAmp > 0.001f) (Math.random().toFloat() * 2f - 1f) * jitterAmp else 0f
                val jy = if (jitterAmp > 0.001f) (Math.random().toFloat() * 2f - 1f) * jitterAmp else 0f
                val jz = if (jitterAmp > 0.001f) (Math.random().toFloat() * 2f - 1f) * jitterAmp else 0f
                rotateVertex(
                    vertsBase[vi][0] * satScale + jx,
                    vertsBase[vi][1] * satScale + jy,
                    vertsBase[vi][2] * satScale + jz,
                    satRx, satRy, 0f,   // independent rotation, no Z
                    tempVerts3d[vi]
                )
            }
            projectSat(tempVerts3d, ox, oy, satScale, draw.W, draw.H, fov, currentSatProj[si])
        }

        // Store into satellite trail ring buffer
        repeat(samples) {
            for(si in 0..1) System.arraycopy(currentSatProj[si],0,satTrailProj[satTrailHead][si],0,16)
            if(satTrailCount<SAT_TRAIL_LEN)satTrailCount++
            satTrailHead=(satTrailHead+1)%SAT_TRAIL_LEN
        }

        // ── Draw satellite trail + current satellites (additive blend) ─────────
        draw.setAdditiveBlend()

        for (age in satTrailCount - 1 downTo 1) {
            val frameIdx = (satTrailHead - 1 - age + SAT_TRAIL_LEN * 2) % SAT_TRAIL_LEN
            val alpha    = (1f - age.toFloat() / satTrailCount.toFloat()) * 0.06f
            for (si in 0..1) {
                val hOff = si.toFloat() * 0.5f
                val proj = satTrailProj[frameIdx][si]
                for ((ei, edge) in edges.withIndex()) {
                    val (a, b) = edge
                    val h     = (fadeHue + hOff + ei.toFloat() / edges.size * 0.4f) % 1f
                    val color = GLDraw.hsl(h, 1f, minOf(.65f,.22f+response.light[0]*.10f*response.accent))
                    draw.line(proj[a * 2], proj[a * 2 + 1], proj[b * 2], proj[b * 2 + 1],
                        color[0], color[1], color[2], alpha)
                }
            }
        }

        // Current satellites at full brightness (treble increases line width shimmer)
        val satL = (minOf(.65f,.22f+response.light[0]*.10f*response.accent) + midM * 0.15f).coerceIn(0f, 1f)
        for (si in 0..1) {
            val hOff = si.toFloat() * 0.5f
            val proj = currentSatProj[si]
            for ((ei, edge) in edges.withIndex()) {
                val (a, b) = edge
                val h     = (fadeHue + hOff + ei.toFloat() / edges.size * 0.4f) % 1f
                val color = GLDraw.hsl(h, 1f, satL)
                draw.line(proj[a * 2], proj[a * 2 + 1], proj[b * 2], proj[b * 2 + 1],
                    color[0], color[1], color[2], 1f)
            }
        }

        draw.setNormalBlend()
    }

    /**
     * Apply Rx * Ry * Rz rotation to a vertex.
     * Writes output to out parameter.
     */
    private fun rotateVertex(vx: Float, vy: Float, vz: Float, rx: Float, ry: Float, rz: Float, out: FloatArray) {
        // Rotate around Z
        val cosZ = cos(rz); val sinZ = sin(rz)
        val x1 = vx * cosZ - vy * sinZ
        val y1 = vx * sinZ + vy * cosZ
        val z1 = vz

        // Rotate around Y
        val cosY = cos(ry); val sinY = sin(ry)
        val x2 = x1 * cosY + z1 * sinY
        val y2 = y1
        val z2 = -x1 * sinY + z1 * cosY

        // Rotate around X
        val cosX = cos(rx); val sinX = sin(rx)
        val x3 = x2
        val y3 = y2 * cosX - z2 * sinX
        val z3 = y2 * sinX + z2 * cosX

        out[0] = x3
        out[1] = y3
        out[2] = z3
    }

    /**
     * Perspective projection.
     * Writes screen coordinates to outProj at offset.
     */
    private fun project(v: FloatArray, W: Int, H: Int, fov: Float, outProj: FloatArray, offset: Int) {
        val sz = maxOf(v[2] + 3.8f, 0.5f)
        val sx = (v[0] * fov / sz + W / 2f).coerceIn(0f, W.toFloat())
        val sy = (v[1] * fov / sz + H / 2f).coerceIn(0f, H.toFloat())
        outProj[offset] = sx
        outProj[offset + 1] = sy
    }

    /**
     * Project satellite cube without distortion.
     * Orbit centre projected once with uniform 2-D scale; all vertices offset
     * from that screen centre. Centre clamped so satellite never leaves screen.
     * Writes screen coordinates to outProj.
     */
    private fun projectSat(verts3d: Array<FloatArray>, ox: Float, oy: Float,
                           satScale: Float, W: Int, H: Int, fov: Float, outProj: FloatArray) {
        val z      = 3.8f
        val scaleS = fov / z
        var cxS    = ox * scaleS + W / 2f
        var cyS    = oy * scaleS + H / 2f
        val extent = satScale * scaleS + 2f
        // On tiny preview surfaces the projected satellite can be wider than
        // the viewport. Cap the clamp margin at half the dimension so the
        // coerce range never becomes inverted.
        val extentX = minOf(extent, W.coerceAtLeast(1) / 2f)
        val extentY = minOf(extent, H.coerceAtLeast(1) / 2f)
        cxS = cxS.coerceIn(extentX, W - extentX)
        cyS = cyS.coerceIn(extentY, H - extentY)
        for (vi in 0 until 8) {
            outProj[vi * 2] = cxS + verts3d[vi][0] * scaleS
            outProj[vi * 2 + 1] = cyS + verts3d[vi][1] * scaleS
        }
        fitViewport(outProj,W,H)
    }
}
