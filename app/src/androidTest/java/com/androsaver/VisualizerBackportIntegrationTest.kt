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
    /** Scan the frame for any lit pixel; effects like Cymatica leave the centre dark. */
    private fun anyBrightPixel():Boolean {
        val bytes=ByteBuffer.allocateDirect(4)
        for(y in 0 until 240 step 16)for(x in 0 until 320 step 16) {
            GLES20.glReadPixels(x,y,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
            if((0..2).any{ (bytes.get(it).toInt() and 255) > 3 })return true
        }
        return false
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
    @Test fun chromaWeaveRetainsColourAndMotionAcrossResizeAndContextRecreation() {
        val mode=com.androsaver.visualizer.modes.ChromaWeaveMode()
        repeat(2) {
            withGl {
                val draw=GLDraw(320,240);draw.onSurfaceCreated();draw.onSurfaceChanged(320,240)
                draw.bloomEnabled=false;mode.onSurfaceCreated();mode.reset()
                val audio=AudioData(mid=.4f,treble=.6f,visualProfile="tv")
                fun frame(tick:Int):ByteArray {
                    draw.beginFrame(true);mode.draw(draw,audio,tick);draw.endFrame()
                    val bytes=ByteBuffer.allocateDirect(draw.W*draw.H*4)
                    GLES20.glReadPixels(0,0,draw.W,draw.H,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
                    return ByteArray(bytes.capacity()).also{bytes.position(0);bytes.get(it)}
                }
                val quiet=frame(0)
                audio.beat=1.2f
                var loud=quiet
                repeat(5){tick->loud=frame(tick+1)}
                assertFalse("Weave must respond to a kick",quiet.contentEquals(loud))
                var saturated=0
                for(i in 0 until loud.size/4) {
                    val rgb=(0..2).map{loud[i*4+it].toInt() and 255}
                    if(rgb.max()-rgb.min()>35)saturated++
                }
                assertTrue("Weave must retain coloured midtones",saturated>320*240/5)
                draw.onSurfaceChanged(240,180)
                assertEquals(240*180*4,frame(6).size)
                assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
            }
        }
    }

    @Test fun magnetarRendersAColouredFieldBeyondItsParticles() {
        val mode=com.androsaver.visualizer.modes.MagnetarMode()
        // Reuse the mode through two EGL contexts to catch stale shader handles.
        repeat(2) {
            withGl {
                val draw=GLDraw(320,240)
                draw.onSurfaceCreated();mode.onSurfaceCreated()
                for((width,height) in listOf(320 to 240,240 to 180)) {
                    draw.onSurfaceChanged(width,height);draw.bloomEnabled=false
                    draw.clearAllBuffers();mode.reset()
                    // First silent frame: require coverage beyond the absolute
                    // particle-only maximum (4,000 rectangles of 3x3 pixels).
                    draw.beginFrame(true);mode.draw(draw,AudioData(),0);draw.endFrame()
                    val pixels=ByteBuffer.allocateDirect(width*height*4)
                    GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels)
                    var covered=0
                    var minimum=255
                    var maximum=0
                    for(index in 0 until width*height) {
                        val r=pixels.get(index*4).toInt() and 255
                        val g=pixels.get(index*4+1).toInt() and 255
                        val b=pixels.get(index*4+2).toInt() and 255
                        val light=maxOf(r,g,b)
                        if(light>2)covered++
                        minimum=minOf(minimum,light);maximum=maxOf(maximum,light)
                    }
                    assertTrue("Magnetar's field is missing at ${width}x$height",covered>maxOf(width*height*.60,4_000*9.0))
                    assertTrue("Magnetar's field must have visible contrast",maximum-minimum>8)
                    repeat(12){tick->
                        draw.beginFrame(false)
                        mode.draw(draw,AudioData(beat=1f,mid=.5f,treble=.7f),tick+1)
                        draw.endFrame()
                    }
                    assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
                }
            }
        }
    }
    @Test fun allThirtyTwoEffectsCompileRenderResizeAndSwitchThroughBlackFrames() = withGl {
        val renderer=VisualizerRenderer(AudioEngine())
        var failure:Throwable?=null
        renderer.onRenderError={failure=it}
        renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,320,240)
        assertEquals(36,renderer.modes.size)
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
            if(mode.name in setOf("Ferrofluid","Phason","Hyperbolic","ChromaWeave","Caustics"))assertTrue("${mode.name} invisible",pixel().any{it>0})
            if(mode.name in setOf("Cymatica","LiquidLight"))assertTrue("${mode.name} invisible",anyBrightPixel())
            renderer.onSurfaceChanged(null,240,180);repeat(4){renderer.onDrawFrame(null)}
            assertNull("${mode.name} resize failed",failure)
            renderer.onSurfaceChanged(null,320,240)
        }
    }
}
