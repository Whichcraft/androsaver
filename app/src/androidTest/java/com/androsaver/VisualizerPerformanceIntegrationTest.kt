package com.androsaver

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import com.androsaver.visualizer.*
import com.androsaver.visualizer.modes.SlimeMoldMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

class VisualizerPerformanceIntegrationTest {
    private fun withGl(width:Int,height:Int,block:()->Unit) {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(display,IntArray(1),0,IntArray(1),0))
        val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
            EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE),0,configs,0,1,count,0)&&count[0]>0)
        val config=requireNotNull(configs[0])
        val context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val surface=EGL14.eglCreatePbufferSurface(display,config,
            intArrayOf(EGL14.EGL_WIDTH,width,EGL14.EGL_HEIGHT,height,EGL14.EGL_NONE),0)
        check(context!=EGL14.EGL_NO_CONTEXT&&surface!=EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(display,surface,surface,context))
        try {block()} finally {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
        }
    }

    @Test fun slimeRendersFineFilteredTrailsAndRecreatesItsTexture() {
        val mode=SlimeMoldMode()
        repeat(2) {
            withGl(320,240) {
                val draw=GLDraw(320,240);draw.onSurfaceCreated();draw.onSurfaceChanged(320,240)
                draw.bloomEnabled=false;mode.onSurfaceCreated()
                repeat(6){tick->draw.beginFrame(true);mode.draw(draw,AudioData(),tick);draw.endFrame()}
                val pixels=ByteBuffer.allocateDirect(320*240*4)
                GLES20.glReadPixels(0,0,320,240,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels)
                assertTrue("Chemical trail must be visible",(0 until 320*240).any{i->
                    (pixels.get(i*4).toInt() and 255)>3 || (pixels.get(i*4+1).toInt() and 255)>3 ||
                        (pixels.get(i*4+2).toInt() and 255)>3})
                val texture=SlimeMoldMode::class.java.getDeclaredField("texture").apply{isAccessible=true}.getInt(mode)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
                val filter=IntArray(1)
                for(parameter in intArrayOf(GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_TEXTURE_MAG_FILTER)) {
                    GLES20.glGetTexParameteriv(GLES20.GL_TEXTURE_2D,parameter,filter,0)
                    assertEquals(GLES20.GL_LINEAR,filter[0])
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0)
                // Seed a known fine-grained field without particles and compare
                // shader colours to desktop's sine-channel palette formula.
                val type=SlimeMoldMode::class.java
                type.getDeclaredField("agentCount").apply{isAccessible=true}.setInt(mode,0)
                val trailField=type.getDeclaredField("trail").apply{isAccessible=true}
                val trail=trailField.get(mode) as FloatArray
                for(y in 0 until 240)for(x in 0 until 320)trail[y*320+x]=5f*(.15f+.7f*x/319f)
                draw.beginFrame(true);mode.draw(draw,AudioData(),6);draw.endFrame()
                val after=trailField.get(mode) as FloatArray
                val hue=type.getDeclaredField("hue").apply{isAccessible=true}.getFloat(mode)
                for(x in intArrayOf(80,160,240)) {
                    val intensity=((after[120*320+x]/5f).coerceIn(0f,1f)*255f).toInt()/255.0
                    val sample=ByteBuffer.allocateDirect(4)
                    GLES20.glReadPixels(x,119,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,sample)
                    for((channel,offset) in doubleArrayOf(0.0,.333,.667).withIndex()) {
                        val expected=((sin(2*PI*(hue+intensity*.5+offset))*127+128)*intensity*2).coerceIn(0.0,255.0)
                        assertEquals("Desktop palette mismatch",expected,(sample.get(channel).toInt() and 255).toDouble(),3.0)
                    }
                }
                draw.onSurfaceChanged(240,180)
                draw.beginFrame(true);mode.draw(draw,AudioData(beat=1f,mid=.4f,treble=.6f),7);draw.endFrame()
                assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
            }
        }
    }

    /** Opt in on the GitHub builder/device; software emulators do not rank TV GPUs. */
    @Test fun reportAllModeFrameTimesAt1080p() {
        assumeTrue("Enable fpsSurvey for the deferred performance run",
            InstrumentationRegistry.getArguments().getString("fpsSurvey")=="true")
        val profile=InstrumentationRegistry.getArguments().getString("fpsProfile") ?: "laptop"
        require(profile=="laptop" || profile=="tv") { "fpsProfile must be laptop or tv" }
        withGl(1920,1080) {
            val draw=GLDraw(1920,1080);draw.onSurfaceCreated();draw.onSurfaceChanged(1920,1080)
            val modes=VisualizerRenderer(AudioEngine()).modes
            assertEquals("Survey must cover the complete current registry",33,modes.size)
            assertEquals("Mode names must identify unique results",modes.size,modes.map{it.name}.toSet().size)
            val audio=AudioData(mid=.4f,treble=.6f,visualProfile=profile)
            println("FPS_SURVEY renderer=${GLES20.glGetString(GLES20.GL_RENDERER)} viewport=1920x1080 modes=${modes.size} warmup=32 samples=16 profile=$profile dt=${audio.deltaSeconds} waveform=${audio.waveform.size} fft=${audio.fft.size} bloom=${draw.bloomEnabled}")
            for(i in audio.fft.indices)audio.fft[i]=i.toFloat()/audio.fft.lastIndex
            for(i in audio.waveform.indices)audio.waveform[i]=sin(i.toDouble()/audio.waveform.lastIndex*8*PI).toFloat()
            for(mode in modes) {
                mode.onSurfaceCreated();mode.reset();draw.clearAllBuffers()
                val samples=DoubleArray(16)
                repeat(48){tick->
                    audio.beat=if(tick%12<3).9f else .08f
                    GLES20.glFinish()
                    val start=System.nanoTime()
                    draw.beginFrame(tick==0);mode.draw(draw,audio,tick);draw.endFrame()
                    GLES20.glFinish()
                    if(tick>=32)samples[tick-32]=(System.nanoTime()-start)/1_000_000.0
                }
                samples.sort()
                val median=(samples[7]+samples[8])*.5
                // Linear percentile interpolation, as in the desktop NumPy survey.
                val p95Position=.95*(samples.size-1)
                val lower=p95Position.toInt()
                val upper=minOf(lower+1,samples.lastIndex)
                val p95=samples[lower]+(samples[upper]-samples[lower])*(p95Position-lower)
                assertTrue("${mode.name} must produce finite positive samples",samples.all{it.isFinite() && it>0.0})
                println("FPS_SURVEY mode=${mode.name} profile=$profile bloom=${draw.bloomEnabled} median_ms=$median p95_ms=$p95 max_ms=${samples.last()} uncapped_fps=${1000.0/median}")
                assertEquals("${mode.name} GL error",GLES20.GL_NO_ERROR,GLES20.glGetError())
            }
        }
    }
}
