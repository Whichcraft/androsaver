package com.androsaver

import com.androsaver.visualizer.*
import com.androsaver.visualizer.modes.TriFluxMode
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class Psysuals320Test {
    @Test fun smoothingHasTheSameResponseAtThirtySixtyAndOneHundredTwentyFps() {
        val results=listOf(30,60,120).map { fps ->
            val dynamics=EffectDynamics();val audio=AudioData(beat=1f,mid=.5f,treble=.2f,deltaSeconds=1f/fps,visualProfile="tv")
            repeat(fps){dynamics.update(audio,1920)}
            assertEquals(.85f,dynamics.motionScale);assertEquals(1.3f,dynamics.accent)
            dynamics.motion.copyOf() to dynamics.light.copyOf()
        }
        for(result in results.drop(1))for(i in 0..2){assertEquals(results[0].first[i],result.first[i],1e-5f);assertEquals(results[0].second[i],result.second[i],1e-5f)}
        val r=EffectDynamics();r.update(AudioData(deltaSeconds=Float.NaN,beat=Float.NaN),3840)
        assertEquals(1f,r.step);assertTrue(r.motion.all{it.isFinite()})
        r.update(AudioData(deltaSeconds=10f),1920);assertEquals(6f,r.step)
    }
    @Test fun sphereTilesShareEdgesAndHorizonClippingNeverDrawsBackFaces() {
        val tiles=HyperbolicMesh.tiles()
        assertEquals(162,tiles.size)
        assertEquals(12,tiles.count{it.size==5})
        val edges=mutableMapOf<Set<HyperbolicMesh.V>,Int>()
        for(tile in tiles)for(i in tile.indices){val key=setOf(tile[i],tile[(i+1)%tile.size]);edges[key]=(edges[key]?:0)+1}
        assertTrue(edges.values.all{it==2})
        for(tile in tiles)assertTrue(HyperbolicMesh.clipFront(tile).all{it.z>=1f/3.2f-1e-6f})
    }
    @Test fun resizePreservesFieldRangeAndCorners() {
        val field=FieldResize.resize(floatArrayOf(0f,1f,2f,3f),2,2,5,5)
        assertEquals(0f,field.first());assertEquals(3f,field.last());assertEquals(1.5f,field[12],1e-6f)
        assertTrue(field.all{it in 0f..3f})
    }
    @Test fun sustainedBassActivatesOnlyOneTriangleUntilAnotherBeatEdge() {
        val mode=TriFluxMode();mode.reset()
        val draw=NoOpDraw();val audio=AudioData(beat=1f)
        repeat(80){mode.draw(draw,audio,it)}
        val field=TriFluxMode::class.java.getDeclaredField("activeIds").apply{isAccessible=true}
        assertEquals(1,(field.get(mode) as List<*>).size)
        audio.beat=0f;mode.draw(draw,audio,80);audio.beat=1f;mode.draw(draw,audio,81)
        assertEquals(2,(field.get(mode) as List<*>).size)
    }
    private class NoOpDraw:GLDraw(640,360) {
        override fun rect(x:Float,y:Float,w:Float,h:Float,r:Float,g:Float,b:Float,a:Float) {}
        override fun polygon(pts:FloatArray,pointCount:Int,r:Float,g:Float,b:Float,a:Float,filled:Boolean) {}
        override fun antiAliasedLine(x1:Float,y1:Float,x2:Float,y2:Float,r:Float,g:Float,b:Float,a:Float,width:Float) {}
    }
}
