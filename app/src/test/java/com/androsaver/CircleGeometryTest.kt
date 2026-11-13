package com.androsaver

import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VertexBatchStorage
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class CircleGeometryTest {
    @Test fun cachedAndFallbackCirclesPreserveEverySubmittedVertex() {
        for (segments in listOf(-1, 0, 1, 6, 8, 12, 20, 24, 96, 128, 129)) {
            for (filled in listOf(false, true)) {
                val draw = GLDraw(320, 240)
                repeat(2) { pass ->
                    val cx=13.5f+pass;val cy=-7.25f;val radius=4.7f+pass
                    draw.circle(cx,cy,radius,.1f,.2f,.3f,.4f,filled,segments)
                    val countField=GLDraw::class.java.getDeclaredField(if(filled) "triCount" else "lineCount").apply{isAccessible=true}
                    val count=countField.getInt(draw)
                    assertEquals((pass+1)*segments.coerceAtLeast(0)*(if(filled)3 else 2),count)
                    val storage=GLDraw::class.java.getDeclaredField("vertexStorage").apply{isAccessible=true}.get(draw) as VertexBatchStorage
                    val actual=if(count>0)storage.snapshot(count*6) else FloatArray(0)
                    var offset=pass*segments.coerceAtLeast(0)*(if(filled)3 else 2)*6
                    fun vertex(x:Float,y:Float) {
                        for(value in floatArrayOf(x,y,.1f,.2f,.3f,.4f))assertEquals(value,actual[offset++],0f)
                    }
                    val step=(2.0*PI/segments).toFloat()
                    for(i in 0 until segments) {
                        val a0=i*step;val a1=a0+step
                        if(filled)vertex(cx,cy)
                        vertex(cx+cos(a0)*radius,cy+sin(a0)*radius)
                        vertex(cx+cos(a1)*radius,cy+sin(a1)*radius)
                    }
                    val cache=GLDraw::class.java.getDeclaredField("circleDirections").apply{isAccessible=true}.get(draw) as Array<*>
                    assertEquals(129,cache.size)
                    assertEquals(if(segments in 1..128)1 else 0,cache.count{it!=null})
                }
            }
        }
    }
}
