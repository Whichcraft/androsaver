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
 *
 * ## Bloom post-processing
 * After the main scene is flushed, a 3-pass bloom pipeline runs:
 *   1. Threshold extract — isolate bright pixels into a half-res FBO.
 *   2. Separable 9-tap Gaussian blur — horizontal pass then vertical pass.
 *   3. Composite — add the blurred bloom layer over the scene on the display.
 * Disable with [bloomEnabled] = false.  Tune with [bloomStrength] / [bloomThreshold].
 */
class GLDraw(var W: Int, var H: Int) {

    // ── Main draw shader ───────────────────────────────────────────────────────

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

    // ── Bloom shader sources ───────────────────────────────────────────────────

    /** Shared vertex shader for all fullscreen-quad passes. */
    private val QUAD_VERT = """
        attribute vec2 aPos;
        varying vec2 vUv;
        void main() {
            vUv = aPos * 0.5 + 0.5;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """.trimIndent()

    /** Luminance-threshold extract: keeps only pixels brighter than [bloomThreshold]. */
    private val THRESH_FRAG = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform float uThreshold;
        varying vec2 vUv;
        void main() {
            vec4 c = texture2D(uTex, vUv);
            float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
            float w = smoothstep(uThreshold, uThreshold + 0.3, luma);
            gl_FragColor = vec4(c.rgb * w, 1.0);
        }
    """.trimIndent()

    /** 9-tap separable Gaussian blur.  Pass (1/W,0) for horizontal, (0,1/H) for vertical. */
    private val BLUR_FRAG = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform vec2 uTexelDir;
        varying vec2 vUv;
        void main() {
            vec4 col = texture2D(uTex, vUv)                       * 0.2270270270;
            col += texture2D(uTex, vUv + uTexelDir * 1.0) * 0.1945945946;
            col += texture2D(uTex, vUv - uTexelDir * 1.0) * 0.1945945946;
            col += texture2D(uTex, vUv + uTexelDir * 2.0) * 0.1216216216;
            col += texture2D(uTex, vUv - uTexelDir * 2.0) * 0.1216216216;
            col += texture2D(uTex, vUv + uTexelDir * 3.0) * 0.0540540541;
            col += texture2D(uTex, vUv - uTexelDir * 3.0) * 0.0540540541;
            col += texture2D(uTex, vUv + uTexelDir * 4.0) * 0.0162162162;
            col += texture2D(uTex, vUv - uTexelDir * 4.0) * 0.0162162162;
            col.a = 1.0;
            gl_FragColor = col;
        }
    """.trimIndent()

    /** Additive composite: scene + bloom * strength. */
    private val COMP_FRAG = """
        precision mediump float;
        uniform sampler2D uScene;
        uniform sampler2D uBloom;
        uniform float uStrength;
        varying vec2 vUv;
        void main() {
            vec4 scene = texture2D(uScene, vUv);
            vec4 bloom = texture2D(uBloom, vUv);
            gl_FragColor = vec4(scene.rgb + bloom.rgb * uStrength, 1.0);
        }
    """.trimIndent()

    // ── GL objects — main pipeline ─────────────────────────────────────────────

    private var program = 0
    private var vbo     = 0
    private var aPos    = 0
    private var aColor  = 0
    private var uProj   = 0
    private val projMatrix = FloatArray(16)

    // ── GL objects — bloom pipeline ────────────────────────────────────────────

    // Fullscreen quad VBO: 6 vertices (2 triangles), each (x,y) in NDC
    private val QUAD_VERTS = floatArrayOf(-1f,-1f, 1f,-1f, 1f,1f, -1f,-1f, 1f,1f, -1f,1f)
    private var quadVbo = 0

    // FBOs: scene at full res, bloomA/bloomB at half res (ping-pong for 2-pass blur)
    private var sceneFboId = 0; private var sceneTexId  = 0
    private var bloomAFboId = 0; private var bloomATexId = 0
    private var bloomBFboId = 0; private var bloomBTexId = 0

    // Bloom program handles + cached uniform/attribute locations
    private var threshProg = 0
    private var threshTexLoc = 0; private var threshThreshLoc = 0; private var threshPosLoc = 0

    private var blurProg = 0
    private var blurTexLoc = 0; private var blurDirLoc = 0; private var blurPosLoc = 0

    private var compProg = 0
    private var compSceneLoc = 0; private var compBloomLoc = 0
    private var compStrLoc = 0; private var compPosLoc = 0

    /** Enable/disable the bloom post-processing pass. Automatically disabled if FBO creation fails. */
    var bloomEnabled: Boolean = true
    /** Additive bloom overlay strength (0 = none, 1 = full). */
    var bloomStrength: Float = 0.5f
    /** Luminance threshold for bloom extraction (0–1). Lower = more glow. */
    var bloomThreshold: Float = 0.35f

    // ── Vertex batches ─────────────────────────────────────────────────────────

    private val STRIDE    = 6           // floats per vertex: x, y, r, g, b, a
    private val MAX_VERTS = 262144      // 256 K vertices ≈ 6 MB
    private val triVerts  = FloatArray(MAX_VERTS * STRIDE)
    private val lineVerts = FloatArray(MAX_VERTS * STRIDE)
    private var triCount  = 0
    private var lineCount = 0

    // Pre-allocated direct FloatBuffers — reused every frame to avoid
    // per-frame ByteBuffer.allocateDirect() calls and GC pressure.
    private val triFloatBuf: FloatBuffer = ByteBuffer
        .allocateDirect(MAX_VERTS * STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val lineFloatBuf: FloatBuffer = ByteBuffer
        .allocateDirect(MAX_VERTS * STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun onSurfaceCreated() {
        program = buildProgram(VERT_SRC, FRAG_SRC)
        aPos   = GLES20.glGetAttribLocation(program, "aPos")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uProj  = GLES20.glGetUniformLocation(program, "uProj")

        val buf = IntArray(1)
        GLES20.glGenBuffers(1, buf, 0); vbo = buf[0]

        // Fullscreen quad VBO (static — upload once)
        GLES20.glGenBuffers(1, buf, 0); quadVbo = buf[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        val qb = ByteBuffer.allocateDirect(QUAD_VERTS.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(QUAD_VERTS).also { it.position(0) }
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, QUAD_VERTS.size * 4, qb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Build and cache bloom shader programs
        threshProg      = buildProgram(QUAD_VERT, THRESH_FRAG)
        threshTexLoc    = GLES20.glGetUniformLocation(threshProg, "uTex")
        threshThreshLoc = GLES20.glGetUniformLocation(threshProg, "uThreshold")
        threshPosLoc    = GLES20.glGetAttribLocation (threshProg, "aPos")

        blurProg   = buildProgram(QUAD_VERT, BLUR_FRAG)
        blurTexLoc = GLES20.glGetUniformLocation(blurProg, "uTex")
        blurDirLoc = GLES20.glGetUniformLocation(blurProg, "uTexelDir")
        blurPosLoc = GLES20.glGetAttribLocation (blurProg, "aPos")

        compProg      = buildProgram(QUAD_VERT, COMP_FRAG)
        compSceneLoc  = GLES20.glGetUniformLocation(compProg, "uScene")
        compBloomLoc  = GLES20.glGetUniformLocation(compProg, "uBloom")
        compStrLoc    = GLES20.glGetUniformLocation(compProg, "uStrength")
        compPosLoc    = GLES20.glGetAttribLocation (compProg, "aPos")

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        W = w; H = h
        GLES20.glViewport(0, 0, w, h)
        Matrix.orthoM(projMatrix, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
        if (bloomEnabled) setupBloomFbos(w, h)
    }

    fun beginFrame() {
        // Render scene into the FBO texture when bloom is active
        if (bloomEnabled && sceneFboId != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFboId)
            GLES20.glViewport(0, 0, W, H)
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        triCount = 0; lineCount = 0
    }

    fun endFrame() {
        flushBatches()
        if (bloomEnabled && sceneFboId != 0) {
            runBloom()
        }
    }

    // ── FBO setup ─────────────────────────────────────────────────────────────

    private fun setupBloomFbos(w: Int, h: Int) {
        deleteBloomFbos()
        val (sf, st) = createFboWithTex(w, h)
        sceneFboId = sf; sceneTexId = st
        if (sf == 0) { bloomEnabled = false; return }

        val bw = (w / 2).coerceAtLeast(1)
        val bh = (h / 2).coerceAtLeast(1)
        val (af, at) = createFboWithTex(bw, bh); bloomAFboId = af; bloomATexId = at
        val (bf, bt) = createFboWithTex(bw, bh); bloomBFboId = bf; bloomBTexId = bt

        if (af == 0 || bf == 0) { deleteBloomFbos(); bloomEnabled = false }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun deleteBloomFbos() {
        fun del(fbo: Int, tex: Int) {
            if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            if (tex != 0) GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
        }
        del(sceneFboId, sceneTexId);   sceneFboId = 0;  sceneTexId = 0
        del(bloomAFboId, bloomATexId); bloomAFboId = 0; bloomATexId = 0
        del(bloomBFboId, bloomBTexId); bloomBFboId = 0; bloomBTexId = 0
    }

    private fun createFboWithTex(w: Int, h: Int): Pair<Int, Int> {
        val ta = IntArray(1); GLES20.glGenTextures(1, ta, 0); val tex = ta[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val fa = IntArray(1); GLES20.glGenFramebuffers(1, fa, 0); val fbo = fa[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, tex, 0)

        val ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) ==
                 GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (!ok) {
            GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            return 0 to 0
        }
        return fbo to tex
    }

    // ── Bloom pipeline ─────────────────────────────────────────────────────────

    private fun runBloom() {
        val bw = (W / 2).coerceAtLeast(1)
        val bh = (H / 2).coerceAtLeast(1)
        GLES20.glDisable(GLES20.GL_BLEND)

        // 1. Threshold extract: scene → bloomA (half res)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomAFboId)
        GLES20.glViewport(0, 0, bw, bh)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(threshProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexId)
        GLES20.glUniform1i(threshTexLoc, 0)
        GLES20.glUniform1f(threshThreshLoc, bloomThreshold)
        drawFullscreenQuad(threshPosLoc)

        // 2. Horizontal blur: bloomA → bloomB
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomBFboId)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(blurProg)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomATexId)
        GLES20.glUniform1i(blurTexLoc, 0)
        GLES20.glUniform2f(blurDirLoc, 1f / bw, 0f)
        drawFullscreenQuad(blurPosLoc)

        // 3. Vertical blur: bloomB → bloomA
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bloomAFboId)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomBTexId)
        GLES20.glUniform2f(blurDirLoc, 0f, 1f / bh)
        drawFullscreenQuad(blurPosLoc)

        // 4. Composite: scene + bloomA → display framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, W, H)
        GLES20.glUseProgram(compProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexId)
        GLES20.glUniform1i(compSceneLoc, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomATexId)
        GLES20.glUniform1i(compBloomLoc, 1)
        GLES20.glUniform1f(compStrLoc, bloomStrength)
        drawFullscreenQuad(compPosLoc)

        // Restore rendering state for the next frame
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun drawFullscreenQuad(posLoc: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    // ── Batch flusher (no bloom — used mid-frame for blend switches) ───────────

    private fun flushBatches() {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uProj, 1, false, projMatrix, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        if (triCount  > 0) flushBatch(triVerts,  triFloatBuf,  triCount,  GLES20.GL_TRIANGLES)
        if (lineCount > 0) flushBatch(lineVerts, lineFloatBuf, lineCount, GLES20.GL_LINES)
    }

    private fun flushBatch(verts: FloatArray, buf: FloatBuffer, count: Int, mode: Int) {
        val floatCount = count * STRIDE
        buf.position(0)
        buf.put(verts, 0, floatCount)
        buf.position(0)

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floatCount * 4, buf, GLES20.GL_DYNAMIC_DRAW)

        val stride = STRIDE * 4
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos,   2, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 2 * 4)
        GLES20.glDrawArrays(mode, 0, count)
    }

    // ── Blending ───────────────────────────────────────────────────────────────

    /** Switch to additive blending — overlapping glows accumulate to white (neon look). */
    fun setAdditiveBlend() {
        flushBatches()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        triCount = 0; lineCount = 0
    }

    /** Restore normal alpha blending. */
    fun setNormalBlend() {
        flushBatches()
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
                val a0 = i * step; val a1 = a0 + step
                addTri(cx, cy,
                       cx + cos(a0) * radius, cy + sin(a0) * radius,
                       cx + cos(a1) * radius, cy + sin(a1) * radius,
                       r, g, b, a)
            }
        } else {
            for (i in 0 until segments) {
                val a0 = i * step; val a1 = a0 + step
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
        val vert = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc)
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
