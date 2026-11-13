package com.androsaver.visualizer.modes

import android.opengl.GLES20
import com.androsaver.visualizer.GLDraw
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GLES2 adaptation of upstream FullscreenField. Uses the current scene FBO, never a readback. */
abstract class FullscreenEffectMode : BaseMode() {
    protected abstract val fragmentShader: String
    private var program = 0
    private var vbo = 0
    private var position = 0
    private val locations = mutableMapOf<String, Int>()
    override fun onSurfaceCreated() { program = 0; vbo = 0; locations.clear() }
    private fun shader(type: Int, code: String): Int {
        val id = GLES20.glCreateShader(type)
        check(id != 0) { "Cannot allocate effect shader" }
        GLES20.glShaderSource(id, code); GLES20.glCompileShader(id)
        val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { val message = GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id); error("Effect shader failed: $message") }
        return id
    }
    private fun initialise() {
        if (program != 0) return
        val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPos; varying vec2 vUV; void main(){ vUV=aPos*.5+.5; gl_Position=vec4(aPos,0.,1.); }")
        var fragment = 0
        var candidate = 0
        try {
            fragment = shader(GLES20.GL_FRAGMENT_SHADER, "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"+fragmentShader)
            candidate = GLES20.glCreateProgram(); check(candidate != 0)
            GLES20.glAttachShader(candidate, vertex); GLES20.glAttachShader(candidate, fragment); GLES20.glLinkProgram(candidate)
            val status = IntArray(1); GLES20.glGetProgramiv(candidate, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Effect shader link failed" }
            val data = floatArrayOf(-1f,-1f,1f,-1f,1f,1f,-1f,-1f,1f,1f,-1f,1f)
            val buffer = ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(data).also { it.position(0) }
            val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]; check(vbo != 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size*4, buffer, GLES20.GL_STATIC_DRAW)
            position = GLES20.glGetAttribLocation(candidate,"aPos")
            program = candidate; candidate = 0
        } finally {
            GLES20.glDeleteShader(vertex); if (fragment != 0) GLES20.glDeleteShader(fragment)
            if (candidate != 0) GLES20.glDeleteProgram(candidate)
        }
    }
    protected fun renderField(draw: GLDraw, uniforms: Map<String, FloatArray>) {
        initialise()
        GLES20.glUseProgram(program); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbo)
        GLES20.glEnableVertexAttribArray(position); GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,8,0)
        val values = uniforms + ("u_resolution" to floatArrayOf(draw.W.toFloat(),draw.H.toFloat()))
        for ((key,value) in values) {
            val loc = locations.getOrPut(key) { GLES20.glGetUniformLocation(program,key) }
            when(value.size) { 1 -> GLES20.glUniform1f(loc,value[0]); 2 -> GLES20.glUniform2f(loc,value[0],value[1]); 3 -> GLES20.glUniform3f(loc,value[0],value[1],value[2]) }
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6)
        GLES20.glDisableVertexAttribArray(position); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0)
    }
}
