package com.androsaver.visualizer

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Batched OpenGL ES 2.0 drawing utilities for the music visualizer.
 *
 * Coordinate system: virtual screen (0,0) top-left → (W,H) bottom-right,
 * matching the Python/pygame coordinate convention.  The orthographic projection
 * maps this range to NDC automatically.
 *
 * All draws are accumulated into two batches (triangles / lines) and flushed
 * together in [endFrame] for efficiency.
 */
class GLDraw(var W: Int, var H: Int) {

    // ── Shader source ──────────────────────────────────────────────────────────

    private val VERT_SRC = """
        attribute vec2 aPos;
        attribute vec4 aColor;
        uniform mat4 uProj;
        varying vec4 vColor;
        void main() {
            gl_Position = uProj * vec4(aPos, 0.0, 1.0);
            vColor = aColor;
            gl_PointSize = 3.0;
        }
    """.trimIndent()

    private val FRAG_SRC = """
        precision mediump float;
        varying vec4 vColor;
        void main() { gl_FragColor = vColor; }
    """.trimIndent()

    // ── GL objects ─────────────────────────────────────────────────────────────

    private var program = 0
    private var vbo = 0
    private var aPos = 0
    private var aColor = 0
    private var uProj = 0

    private val projMatrix = FloatArray(16)

    // ── Vertex batches ─────────────────────────────────────────────────────────

    // 6 floats per vertex: x, y, r, g, b, a
    private val STRIDE = 6
    private val MAX_VERTS = 262144          // 256 K vertices ≈ 6 MB
    private val triVerts = FloatArray(MAX_VERTS * STRIDE)
    private val lineVerts = FloatArray(MAX_VERTS * STRIDE)
    private var triCount = 0
    private var lineCount = 0

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun onSurfaceCreated() {
        program = buildProgram(VERT_SRC, FRAG_SRC)
        aPos   = GLES20.glGetAttribLocation(program, "aPos")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uProj  = GLES20.glGetUniformLocation(program, "uProj")

        val buf = IntArray(1)
        GLES20.glGenBuffers(1, buf, 0)
        vbo = buf[0]

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        W = w; H = h
        GLES20.glViewport(0, 0, w, h)
        Matrix.orthoM(projMatrix, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
    }

    fun beginFrame() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        triCount = 0
        lineCount = 0
    }

    fun endFrame() {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uProj, 1, false, projMatrix, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)

        if (triCount > 0) flushBatch(triVerts, triCount, GLES20.GL_TRIANGLES)
        if (lineCount > 0) flushBatch(lineVerts, lineCount, GLES20.GL_LINES)
    }

    private fun flushBatch(verts: FloatArray, count: Int, mode: Int) {
        val floatCount = count * STRIDE
        val buf: FloatBuffer = ByteBuffer
            .allocateDirect(floatCount * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts, 0, floatCount)
            .also { it.position(0) }

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floatCount * 4, buf, GLES20.GL_DYNAMIC_DRAW)

        val stride = STRIDE * 4  // bytes
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos,   2, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 2 * 4)

        GLES20.glDrawArrays(mode, 0, count)
    }

    // ── Blending ───────────────────────────────────────────────────────────────

    /** Switch to additive blending — overlapping glows accumulate to white (neon look). */
    fun setAdditiveBlend() {
        endFrame()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        triCount = 0; lineCount = 0
    }

    /** Restore normal alpha blending. */
    fun setNormalBlend() {
        endFrame()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        triCount = 0; lineCount = 0
    }

    // ── Trail / fade ───────────────────────────────────────────────────────────

    /**
     * Draw a semi-transparent black overlay to create motion-trail persistence.
     * alpha ≈ 0.11 matches the Python `fade.set_alpha(28)` (28/255 ≈ 0.11).
     */
    fun fadeBlack(alpha: Float = 0.11f) {
        rect(0f, 0f, W.toFloat(), H.toFloat(), 0f, 0f, 0f, alpha)
    }

    // ── High-level draw calls ──────────────────────────────────────────────────

    fun rect(x: Float, y: Float, w: Float, h: Float,
             r: Float, g: Float, b: Float, a: Float = 1f) {
        val x2 = x + w; val y2 = y + h
        // Two triangles
        addTri(x, y,  r, g, b, a)
        addTri(x2, y, r, g, b, a)
        addTri(x, y2, r, g, b, a)
        addTri(x2, y, r, g, b, a)
        addTri(x2, y2,r, g, b, a)
        addTri(x, y2, r, g, b, a)
    }

    /** Filled or wireframe circle approximated with [segments] vertices. */
    fun circle(cx: Float, cy: Float, radius: Float,
               r: Float, g: Float, b: Float, a: Float = 1f,
               filled: Boolean = true, segments: Int = 20) {
        val step = (2.0 * PI / segments).toFloat()
        if (filled) {
            for (i in 0 until segments) {
                val a0 = i * step
                val a1 = a0 + step
                addTri(cx, cy,
                       cx + cos(a0) * radius, cy + sin(a0) * radius,
                       cx + cos(a1) * radius, cy + sin(a1) * radius,
                       r, g, b, a)
            }
        } else {
            for (i in 0 until segments) {
                val a0 = i * step
                val a1 = a0 + step
                addLine(cx + cos(a0) * radius, cy + sin(a0) * radius,
                        cx + cos(a1) * radius, cy + sin(a1) * radius,
                        r, g, b, a)
            }
        }
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float,
             r: Float, g: Float, b: Float, a: Float = 1f) {
        addLine(x1, y1, x2, y2, r, g, b, a)
    }

    /** Open polyline through a flat [pts] array [x0,y0, x1,y1, ...]. */
    fun lineStrip(pts: FloatArray, r: Float, g: Float, b: Float, a: Float = 1f) {
        val n = pts.size / 2
        for (i in 0 until n - 1) {
            addLine(pts[i * 2], pts[i * 2 + 1],
                    pts[i * 2 + 2], pts[i * 2 + 3],
                    r, g, b, a)
        }
    }

    /**
     * Polyline with per-vertex colors.
     * [pts] = [x0,y0, x1,y1, ...], [colors] = [r0,g0,b0,a0, r1,g1,b1,a1, ...].
     */
    fun colorLineStrip(pts: FloatArray, colors: FloatArray) {
        val n = pts.size / 2
        for (i in 0 until n - 1) {
            addLine(pts[i * 2],     pts[i * 2 + 1],
                    pts[i * 2 + 2], pts[i * 2 + 3],
                    colors[i * 4], colors[i * 4 + 1], colors[i * 4 + 2], colors[i * 4 + 3],
                    colors[i * 4 + 4], colors[i * 4 + 5], colors[i * 4 + 6], colors[i * 4 + 7])
        }
    }

    /** Closed polygon from [pts] = [x0,y0, x1,y1, ...].  Filled = triangle fan. */
    fun polygon(pts: FloatArray, r: Float, g: Float, b: Float, a: Float = 1f, filled: Boolean = false) {
        val n = pts.size / 2
        if (n < 2) return
        if (filled && n >= 3) {
            val cx = pts.filterIndexed { i, _ -> i % 2 == 0 }.average().toFloat()
            val cy = pts.filterIndexed { i, _ -> i % 2 == 1 }.average().toFloat()
            for (i in 0 until n) {
                val j = (i + 1) % n
                addTri(cx, cy,
                       pts[i * 2], pts[i * 2 + 1],
                       pts[j * 2], pts[j * 2 + 1],
                       r, g, b, a)
            }
        } else {
            for (i in 0 until n) {
                val j = (i + 1) % n
                addLine(pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1], r, g, b, a)
            }
        }
    }

    // ── Vertex emitters ────────────────────────────────────────────────────────

    private fun addTri(x: Float, y: Float, r: Float, g: Float, b: Float, a: Float) {
        if (triCount >= MAX_VERTS) return
        val i = triCount * STRIDE
        triVerts[i] = x;  triVerts[i+1] = y
        triVerts[i+2] = r; triVerts[i+3] = g; triVerts[i+4] = b; triVerts[i+5] = a
        triCount++
    }

    private fun addTri(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
                       r: Float, g: Float, b: Float, a: Float) {
        addTri(x0, y0, r, g, b, a)
        addTri(x1, y1, r, g, b, a)
        addTri(x2, y2, r, g, b, a)
    }

    private fun addLine(x1: Float, y1: Float, x2: Float, y2: Float,
                        r: Float, g: Float, b: Float, a: Float) {
        if (lineCount + 1 >= MAX_VERTS) return
        val i = lineCount * STRIDE
        lineVerts[i]   = x1; lineVerts[i+1] = y1
        lineVerts[i+2] = r;  lineVerts[i+3] = g; lineVerts[i+4] = b; lineVerts[i+5] = a
        val j = (lineCount + 1) * STRIDE
        lineVerts[j]   = x2; lineVerts[j+1] = y2
        lineVerts[j+2] = r;  lineVerts[j+3] = g; lineVerts[j+4] = b; lineVerts[j+5] = a
        lineCount += 2
    }

    /** Per-vertex colored line segment. */
    private fun addLine(x1: Float, y1: Float, x2: Float, y2: Float,
                        r1: Float, g1: Float, b1: Float, a1: Float,
                        r2: Float, g2: Float, b2: Float, a2: Float) {
        if (lineCount + 1 >= MAX_VERTS) return
        val i = lineCount * STRIDE
        lineVerts[i]   = x1; lineVerts[i+1] = y1
        lineVerts[i+2] = r1; lineVerts[i+3] = g1; lineVerts[i+4] = b1; lineVerts[i+5] = a1
        val j = (lineCount + 1) * STRIDE
        lineVerts[j]   = x2; lineVerts[j+1] = y2
        lineVerts[j+2] = r2; lineVerts[j+3] = g2; lineVerts[j+4] = b2; lineVerts[j+5] = a2
        lineCount += 2
    }

    // ── GL helpers ─────────────────────────────────────────────────────────────

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        return GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vert)
            GLES20.glAttachShader(prog, frag)
            GLES20.glLinkProgram(prog)
        }
    }

    private fun compileShader(type: Int, src: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
        }

    // ── Companion: colour helpers ──────────────────────────────────────────────

    companion object {
        /**
         * Convert HSL to RGBA FloatArray.  Matches Python's colorsys.hls_to_rgb
         * (note: Python uses HLS order, we keep standard HSL parameter names here).
         */
        fun hsl(h: Float, s: Float = 1f, l: Float = 0.5f, a: Float = 1f): FloatArray {
            val hh = ((h % 1f + 1f) % 1f) * 6f
            val c = (1f - abs(2f * l - 1f)) * s
            val x = c * (1f - abs(hh % 2f - 1f))
            val m = l - c / 2f
            val (r, g, b) = when (hh.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return floatArrayOf(
                (r + m).coerceIn(0f, 1f),
                (g + m).coerceIn(0f, 1f),
                (b + m).coerceIn(0f, 1f),
                a
            )
        }
    }
}
