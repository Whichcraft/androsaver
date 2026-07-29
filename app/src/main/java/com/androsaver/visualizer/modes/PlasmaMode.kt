package com.androsaver.visualizer.modes

import android.opengl.GLES20
import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Full-screen sine-interference plasma implemented entirely in a GLSL fragment shader.
 * No CPU pixel work — the GPU computes every pixel per frame.
 * Matches the Python Plasma class math exactly.
 */
class PlasmaMode : BaseMode() {

    override val name = "Plasma"

    // ── Shader source ──────────────────────────────────────────────────────────

    private val VERT = """
        attribute vec2 aPos;
        varying vec2 vUV;
        void main() {
            vUV = aPos * 0.5 + 0.5;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """.trimIndent()

    private val FRAG = """
        precision mediump float;
        varying vec2 vUV;
        uniform float uTime;
        uniform float uBass;
        uniform float uMid;
        uniform float uHigh;
        uniform float uBeat;
        uniform float uHue;

        vec3 hsl2rgb(float h, float s, float l) {
            float c  = (1.0 - abs(2.0 * l - 1.0)) * s;
            float hh = mod(h, 1.0) * 6.0;
            float x  = c * (1.0 - abs(mod(hh, 2.0) - 1.0));
            float m  = l - c * 0.5;
            vec3 rgb;
            if      (hh < 1.0) rgb = vec3(c, x, 0.0);
            else if (hh < 2.0) rgb = vec3(x, c, 0.0);
            else if (hh < 3.0) rgb = vec3(0.0, c, x);
            else if (hh < 4.0) rgb = vec3(0.0, x, c);
            else if (hh < 5.0) rgb = vec3(x, 0.0, c);
            else               rgb = vec3(c, 0.0, x);
            return clamp(rgb + m, 0.0, 1.0);
        }

        void main() {
            // Map UV [0,1] → centred coords [-2.5π, 2.5π] matching Python linspace
            const float PI = 3.14159265;
            vec2 uv = (vUV - 0.5) * (5.0 * PI);
            float R  = length(uv);
            float fm = 1.0 + uMid * 0.7;
            float t  = uTime;

            float v = sin(uv.x * fm          + t        )
                    + sin(uv.y * fm * 0.8    + t * 1.4  )
                    + sin((uv.x * 0.6 + uv.y * 0.8) * fm + t * 0.9)
                    + sin(R * fm * 0.5        - t * 1.2  );
            v *= 0.25;

            float h = mod(v * 0.55 + 0.5 + uHue + uBass * 0.35, 1.0);
            float l = clamp(0.28 + v * 0.22 + uBeat * 0.22 + uHigh * 0.08, 0.0, 0.95);

            gl_FragColor = vec4(hsl2rgb(h, 1.0, l), 1.0);
        }
    """.trimIndent()

    // ── GL state ───────────────────────────────────────────────────────────────

    private var program  = 0
    private var vbo      = 0
    private var uTime    = 0; private var uBass = 0; private var uMid  = 0
    private var uHigh    = 0; private var uBeat = 0; private var uHue  = 0
    private var aPos     = 0

    private var time = 0f
    private var hue  = 0f

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun reset() {
        time = 0f
        hue = 0f
    }

    override fun onSurfaceCreated() {
        // A recreated EGL context invalidates all handles from the old context.
        program = 0
        vbo = 0
        reset()
    }

    private fun initGL(): Boolean {
        if (program != 0 && vbo != 0) return true
        val vert = compile(GLES20.GL_VERTEX_SHADER,   VERT)
        val frag = compile(GLES20.GL_FRAGMENT_SHADER, FRAG)
        if (vert == 0 || frag == 0) {
            if (vert != 0) GLES20.glDeleteShader(vert)
            if (frag != 0) GLES20.glDeleteShader(frag)
            return false
        }
        val candidate = GLES20.glCreateProgram()
        if (candidate == 0) {
            GLES20.glDeleteShader(vert)
            GLES20.glDeleteShader(frag)
            return false
        }
        GLES20.glAttachShader(candidate, vert)
        GLES20.glAttachShader(candidate, frag)
        GLES20.glLinkProgram(candidate)
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(candidate, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GLES20.GL_FALSE) {
            GLES20.glDeleteProgram(candidate)
            return false
        }
        program = candidate
        aPos  = GLES20.glGetAttribLocation(program,  "aPos")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uBass = GLES20.glGetUniformLocation(program, "uBass")
        uMid  = GLES20.glGetUniformLocation(program, "uMid")
        uHigh = GLES20.glGetUniformLocation(program, "uHigh")
        uBeat = GLES20.glGetUniformLocation(program, "uBeat")
        uHue  = GLES20.glGetUniformLocation(program, "uHue")

        // Full-screen quad: two triangles covering NDC [-1,1]
        val quad = floatArrayOf(-1f, -1f,  1f, -1f,  1f,  1f,
                                -1f, -1f,  1f,  1f, -1f,  1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(quad).also { it.position(0) }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        if (vbo == 0) {
            GLES20.glDeleteProgram(program)
            program = 0
            return false
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES20.GL_STATIC_DRAW)
        return true
    }

    // ── Draw ───────────────────────────────────────────────────────────────────

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        if (!initGL()) return

        val beat = audio.beat
        val bass = beat
        val mid  = audio.mid
        val high = audio.treble
        hue  += 0.002f
        time += 0.018f + bass * 0.05f + mid * 0.03f + high * 0.02f

        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, 0)

        GLES20.glUniform1f(uTime, time)
        GLES20.glUniform1f(uBass, bass)
        GLES20.glUniform1f(uMid,  mid)
        GLES20.glUniform1f(uHigh, high)
        GLES20.glUniform1f(uBeat, beat)
        GLES20.glUniform1f(uHue,  hue)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        // GLDraw.endFrame() called by VisualizerRenderer restores program + flushes empty batches.
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
