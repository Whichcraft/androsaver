package com.androsaver

import com.androsaver.visualizer.*
import com.androsaver.visualizer.modes.*
import org.junit.Assert.*
import org.junit.Test

/** Behaviour/work budgets, never wall-clock thresholds on shared CI machines. */
class VisualizerPerformanceContractsTest {
    @Test fun slimeUsesFineBoundedFieldsAndPreservesPopulationOnResize() {
        val mode=SlimeMoldMode()
        val init=SlimeMoldMode::class.java.getDeclaredMethod("init",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply{isAccessible=true}
        fun field(name:String):Any=SlimeMoldMode::class.java.getDeclaredField(name).apply{isAccessible=true}.get(mode)
        init.invoke(mode,1920,1080)
        assertEquals(1920,field("gridW"));assertEquals(1080,field("gridH"))
        assertEquals(26000,field("agentCount"))
        val trail=field("trail") as FloatArray
        trail[trail.size/2]=1f
        init.invoke(mode,3840,2160)
        assertSame("Same simulation size at 4K must not allocate/reset the field",trail,field("trail"))
        init.invoke(mode,640,360)
        assertEquals(26000,field("agentCount"))
        assertEquals(640*360,(field("trail") as FloatArray).size)
        assertTrue((field("trail") as FloatArray).all{it.isFinite()})
        init.invoke(mode,480,854)
        assertEquals(480,field("gridW"));assertEquals(854,field("gridH"))
    }

    @Test fun cymaticaKeepsTheAnalyticModePairsWithoutACachedBasis() {
        val mode=CymaticaMode()
        val m=mode.javaClass.getDeclaredField("modeM").apply{isAccessible=true}.get(mode) as IntArray
        val n=mode.javaClass.getDeclaredField("modeN").apply{isAccessible=true}.get(mode) as IntArray
        assertEquals(listOf(1,2,3,4,2,3),m.toList())
        assertEquals(listOf(2,3,4,5,5,6),n.toList())
        // The plate is evaluated per pixel in the shader, so no cached basis is kept.
        assertTrue(mode.javaClass.declaredFields.none{it.name=="modeFields"})
        val weights=mode.javaClass.getDeclaredField("weights").apply{isAccessible=true}.get(mode) as FloatArray
        mode.reset()
        assertEquals(1f,weights[0],0f)
        assertTrue(weights.drop(1).all{it==0f})
    }

    @Test fun branchesRetainsBothNeonPassesForEveryRecursiveSegment() {
        val draw=CountingDraw()
        BranchesMode().draw(draw,AudioData(beat=1f,mid=.4f,treble=.6f),0)
        // 11 arms; full seven-level tree: 1+3+9+18+36+72+144 nodes/arm.
        assertEquals(11*283*2,draw.lines)
        assertTrue(draw.maximumChannel>.5f)
    }

    @Test fun latticeSubmitsEachBeamOnlyOnceWithoutRemovingTheNodeGrid() {
        val mode=LatticeMode()
        val draw=CountingDraw()
        val audio=AudioData(fft=FloatArray(512){1f},beat=1f,visualProfile="laptop")
        mode.draw(draw,audio,0)
        assertEquals(14*21+13*22,draw.lines)
        assertEquals(22*14*4,draw.circles)
        assertTrue(draw.maximumChannel>.85f) // summed additive light survives
    }
    @Test fun hyperbolicBassPulseLiftsFacetsQuicklyThenReleases() {
        val quiet=HyperbolicMode(); val pulsed=HyperbolicMode()
        val draw=CountingDraw()
        fun heights(mode:HyperbolicMode)=HyperbolicMode::class.java.getDeclaredField("heights")
            .apply{isAccessible=true}.get(mode) as FloatArray
        var peak=0.0
        repeat(90){tick->
            quiet.draw(draw,AudioData(),tick)
            pulsed.draw(draw,AudioData(beat=if(tick<6)1.2f else 0f),tick)
            val a=heights(quiet);val b=heights(pulsed)
            val lift=a.indices.map{(b[it]-a[it]).toDouble()}.average()
            if(tick==3)assertTrue("A short kick must visibly lift facets",lift>.015)
            if(tick==5)peak=lift
            if(tick==89)assertTrue("Pulse must settle back",kotlin.math.abs(lift)<peak*.1)
        }
    }

    @Test fun hyperbolicLoudFacetsFitBothDisplayProfiles() {
        for(profile in listOf("laptop","tv")) {
            val mode=HyperbolicMode();val draw=CountingDraw(320,240)
            repeat(120){tick->mode.draw(draw,AudioData(beat=6f,mid=6f,treble=6f,visualProfile=profile),tick)}
            assertTrue(draw.minimumX>=4f);assertTrue(draw.maximumX<=316f)
            assertTrue(draw.minimumY>=4f);assertTrue(draw.maximumY<=236f)
        }
    }
    private class CountingDraw(width:Int=1920,height:Int=1080):GLDraw(width,height) {
        var minimumX=Float.POSITIVE_INFINITY;var minimumY=Float.POSITIVE_INFINITY
        var maximumX=Float.NEGATIVE_INFINITY;var maximumY=Float.NEGATIVE_INFINITY
        var lines=0;var circles=0;var maximumChannel=0f
        override fun setAdditiveBlend() {}
        override fun setNormalBlend() {}
        override fun rect(x:Float,y:Float,w:Float,h:Float,r:Float,g:Float,b:Float,a:Float) {}
        override fun line(x1:Float,y1:Float,x2:Float,y2:Float,r:Float,g:Float,b:Float,a:Float) {
            lines++;maximumChannel=maxOf(maximumChannel,r,g,b)
        }
        override fun circle(cx:Float,cy:Float,radius:Float,r:Float,g:Float,b:Float,a:Float,filled:Boolean,segments:Int) {circles++}
        override fun antiAliasedLine(x1:Float,y1:Float,x2:Float,y2:Float,r:Float,g:Float,b:Float,a:Float,width:Float) {}
        override fun polygon(pts:FloatArray,pointCount:Int,r:Float,g:Float,b:Float,a:Float,filled:Boolean) {
            for(i in 0 until pointCount) {
                assertTrue(pts[i*2].isFinite());assertTrue(pts[i*2+1].isFinite())
                minimumX=minOf(minimumX,pts[i*2]);maximumX=maxOf(maximumX,pts[i*2])
                minimumY=minOf(minimumY,pts[i*2+1]);maximumY=maxOf(maximumY,pts[i*2+1])
            }
        }
    }
}
