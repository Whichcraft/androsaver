package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.ChromaWeaveMode
import org.junit.Assert.*
import org.junit.Test

class ChromaWeaveModeTest {
    @Test fun shortKickReleasesAndProfileAccentsStayBounded() {
        val mode=ChromaWeaveMode()
        val draw=GLDraw(320,240)
        val advance=ChromaWeaveMode::class.java.getDeclaredMethod("advance",GLDraw::class.java,AudioData::class.java).apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST")
        val uniforms=ChromaWeaveMode::class.java.getDeclaredField("uniforms").apply{isAccessible=true}.get(mode) as Map<String,FloatArray>
        for(profile in listOf("laptop","tv")) {
            mode.reset()
            val audio=AudioData(beat=1.2f,visualProfile=profile)
            repeat(5){advance.invoke(mode,draw,audio)}
            assertTrue(uniforms.getValue("u_pulse")[0]>.8f)
            assertEquals(if(profile=="tv")1.3f else 1f,uniforms.getValue("u_accent")[0],0f)
            val time=uniforms.getValue("u_time")[0]
            audio.deltaSeconds=0f;advance.invoke(mode,draw,audio)
            assertEquals(time,uniforms.getValue("u_time")[0],0f)
            audio.deltaSeconds=1f/60f;audio.beat=0f
            repeat(200){advance.invoke(mode,draw,audio)}
            assertTrue(uniforms.getValue("u_pulse")[0]<.00001f)
            assertTrue(uniforms.getValue("u_bass")[0]<.002f)
            assertTrue(uniforms.values.all{it[0].isFinite()})
        }
    }
}
