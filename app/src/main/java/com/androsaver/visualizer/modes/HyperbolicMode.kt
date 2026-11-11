package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.HyperbolicMesh
import com.androsaver.visualizer.HyperbolicMesh.V
import kotlin.math.*

/** Connected sphere mesh with desktop-reference bass lift, light and viewport fit. */
class HyperbolicMode : BaseMode() {
    override val name = "Hyperbolic"
    private val tiles = HyperbolicMesh.tiles()
    private val centers = tiles.map { tile -> tile.reduce { a,b -> a+b }*(1f/tile.size) }
    private val heights = FloatArray(tiles.size)
    private val phases = FloatArray(tiles.size)
    private val longitudes = FloatArray(tiles.size) { atan2(centers[it].y,centers[it].x) }
    private val pivots = tiles.map { (it[0]+it[1])*.5f }
    private val axes = tiles.map { (it[1]-it[0]).normal() }
    private val relatives = tiles.mapIndexed { i,tile -> tile.map { it-pivots[i] } }
    private val crossed = relatives.mapIndexed { i,points -> points.map { axes[i].cross(it) } }
    private val along = relatives.mapIndexed { i,points -> points.map { it.dot(axes[i]) } }
    private val depths = DoubleArray(tiles.size)
    private var cosAngle = 1f
    private var sinAngle = 0f
    private val cosTilt = cos(.35f)
    private val sinTilt = sin(.35f)
    private var time = 0f
    private var angle = 0f
    private var hue = 0f
    private var projectionScale = 1f
    private data class Segment(val index:Int, val base:List<V>, val visible:List<V>, val cap:List<V>)

    override fun reset() {
        time=0f; angle=0f; hue=0f; heights.fill(0f)
    }
    private fun rotate(v:V):V {
        val x=v.x*cosAngle+v.z*sinAngle
        val z=-v.x*sinAngle+v.z*cosAngle
        return V(x,v.y*cosTilt-z*sinTilt,v.y*sinTilt+z*cosTilt)
    }
    private fun paint(draw:GLDraw,vertices:List<V>,h:Float,l:Float,filled:Boolean) {
        val clipped=HyperbolicMesh.clipFront(vertices)
        if(clipped.size<3)return
        val p=FloatArray(clipped.size*2)
        for(i in clipped.indices) {
            val v=clipped[i]
            val s=projectionScale*3.2f/(3.2f-v.z)
            p[i*2]=draw.W*.5f+v.x*s
            p[i*2+1]=draw.H*.5f+v.y*s
        }
        val c=GLDraw.hsl(((h%1f)+1f)%1f,.95f,l.coerceIn(0f,1f))
        if(filled)draw.polygon(p,c[0],c[1],c[2],1f,true)
        else for(i in clipped.indices) {
            val j=(i+1)%clipped.size
            draw.antiAliasedLine(p[i*2],p[i*2+1],p[j*2],p[j*2+1],c[0],c[1],c[2])
        }
    }
    override fun draw(draw:GLDraw,audio:AudioData,tick:Int) {
        val response=prepare(draw,audio)
        val mid=response.motion[1]
        val bassPulse=response.light[0]
        time+=(.013f+mid*.002f)*response.step
        angle+=(.008f+mid*.002f)*response.step*response.motionScale
        hue=(hue+(.0025f+mid*.0008f)*response.step)%1f
        draw.rect(0f,0f,draw.W.toFloat(),draw.H.toFloat(),2f/255f,1f/255f,8f/255f,1f)
        draw.circle(draw.W*.5f,draw.H*.5f,minOf(draw.W,draw.H)*.36f*3.2f/sqrt(3.2f*3.2f-1f),.035f,.055f,.12f,1f,true,96)
        cosAngle=cos(angle); sinAngle=sin(angle)
        val rotated=tiles.map { tile -> tile.map(::rotate) }
        for(i in rotated.indices)depths[i]=rotated[i].sumOf { it.z.toDouble() }/rotated[i].size
        val segments=ArrayList<Segment>(tiles.size)
        for(index in tiles.indices.sortedBy { depths[it] }) {
            val center=centers[index]
            val phase=time+longitudes[index]*3f+center.z*4f
            phases[index]=phase
            val idle=.075f*sin(phase)+.035f*sin(time*1.4f+index*.52f)
            val lift=(response.motion[0]*.055f+bassPulse*.11f)*(.5f+.5f*sin(phase+time*.3f))
            heights[index]=response.smooth(heights[index],(idle+lift).coerceIn(-.10f,.18f),
                .08f+.16f*minOf(bassPulse,1f))
            val base=rotated[index]
            val visible=HyperbolicMesh.clipFront(base)
            if(visible.size<3)continue
            val a=(.30f+bassPulse*.04f)*maxOf(0f,sin(phase))
            val ca=cos(a); val sa=sin(a)
            val cap=tiles[index].indices.map { i ->
                rotate((relatives[index][i]*ca+crossed[index][i]*sa+
                    axes[index]*(along[index][i]*(1f-ca))+pivots[index])*(1f+heights[index]))
            }
            segments.add(Segment(index,base,visible,cap))
        }
        projectionScale=minOf(draw.W,draw.H)*.36f
        var extentX=1f; var extentY=1f
        for(segment in segments)for(v in segment.cap) {
            val scale=projectionScale*3.2f/(3.2f-v.z)
            extentX=maxOf(extentX,abs(v.x*scale)); extentY=maxOf(extentY,abs(v.y*scale))
        }
        val margin=minOf(4.001f,minOf(draw.W,draw.H)*.04f)
        val availableX=maxOf(1f,draw.W*.5f-margin)
        val availableY=maxOf(1f,draw.H*.5f-margin)
        projectionScale*=minOf(1f,availableX/extentX,availableY/extentY)
        for(segment in segments) {
            val index=segment.index
            val base=segment.base; val cap=segment.cap
            val height=heights[index]
            val depth=segment.visible.map { it.z }.average().toFloat()
            val h=hue+index*.033f+.20f*sin(phases[index])+height*.8f
            for(i in base.indices) {
                val j=(i+1)%base.size
                paint(draw,listOf(base[i],base[j],cap[j],cap[i]),h+.10f,
                    .04f+maxOf(0f,height)*.5f+(i%3)*.018f,true)
            }
            paint(draw,cap,h,minOf(.62f,.13f+depth*.12f+maxOf(0f,height)*.65f+
                bassPulse*.18f*response.accent),true)
            paint(draw,cap,h+.08f,minOf(.90f,.42f+depth*.22f+
                response.light[2]*.20f+maxOf(0f,height)),false)
        }
    }
}
