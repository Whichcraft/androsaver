package com.androsaver

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import com.androsaver.visualizer.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class VisualizerBackportIntegrationTest {
    private fun withGl(block:()->Unit) {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display,IntArray(1),0,IntArray(1),0))
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
        val attrs=intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)&&count[0]>0)
        val config=requireNotNull(configs[0])
        val context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val surface=EGL14.eglCreatePbufferSurface(display,config,intArrayOf(EGL14.EGL_WIDTH,320,EGL14.EGL_HEIGHT,240,EGL14.EGL_NONE),0)
        check(EGL14.eglMakeCurrent(display,surface,surface,context))
        try { block() } finally {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
        }
    }
    private fun pixel(x:Int=160,y:Int=120):List<Int> {
        val bytes=ByteBuffer.allocateDirect(4)
        GLES20.glReadPixels(x,y,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
        return (0..2).map{bytes.get(it).toInt() and 255}
    }
    @Test fun clearingAnEffectRemovesSceneBloomAndDisplayPixels() = withGl {
        val draw=GLDraw(320,240);draw.onSurfaceCreated();draw.onSurfaceChanged(320,240)
        for(bloom in listOf(true,false)) {
            draw.bloomEnabled=bloom
            draw.beginFrame(true);draw.rect(0f,0f,320f,240f,1f,.5f,.2f,1f);draw.endFrame()
            assertTrue(pixel().any{it>0})
            draw.clearAllBuffers()
            assertTrue(pixel().all{it==0})
            draw.beginFrame(false);draw.endFrame()
            assertTrue("Retained scene or bloom leaked after clear",pixel().all{it==0})
            assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
        }
    }
    @Test fun allThirtyTwoEffectsCompileRenderResizeAndSwitchThroughBlackFrames() = withGl {
        val renderer=VisualizerRenderer(AudioEngine())
        var failure:Throwable?=null
        renderer.onRenderError={failure=it}
        renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,320,240)
        assertEquals(32,renderer.modes.size)
        val expected=InstrumentationRegistry.getInstrumentation().targetContext.resources.getStringArray(R.array.viz_effect_values).toList()
        assertEquals(expected,renderer.modeNames)
        for((index,mode) in renderer.modes.withIndex()) {
            renderer.modeIndex=index
            renderer.onDrawFrame(null)
            assertTrue("Outgoing pixels survived switching to ${mode.name}",pixel().all{it==0})
            renderer.onDrawFrame(null)
            repeat(3){renderer.onDrawFrame(null)}
            assertNull("${mode.name} failed",failure)
            assertEquals("${mode.name} GL error",GLES20.GL_NO_ERROR,GLES20.glGetError())
            if(mode.name in setOf("Ferrofluid","Phason","Hyperbolic"))assertTrue("${mode.name} invisible",pixel().any{it>0})
            renderer.onSurfaceChanged(null,240,180);repeat(4){renderer.onDrawFrame(null)}
            assertNull("${mode.name} resize failed",failure)
            renderer.onSurfaceChanged(null,320,240)
        }
    }
}
