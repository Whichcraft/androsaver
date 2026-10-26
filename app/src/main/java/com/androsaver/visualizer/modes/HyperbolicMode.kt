package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.HyperbolicMesh
import com.androsaver.visualizer.HyperbolicMesh.V
import kotlin.math.*

/** Connected sphere mesh with independently lifting, hinging and sinking caps. */
class HyperbolicMode : BaseMode() {
    override val name="Hyperbolic"
    private val tiles=HyperbolicMesh.tiles()
    private val centers=tiles.map { tile -> tile.reduce { a,b -> a+b }*(1f/tile.size) }
    private val heights=FloatArray(tiles.size)
    private var time=0f;private var angle=0f;private var hue=0f
    override fun reset() { time=0f;angle=0f;hue=0f;heights.fill(0f) }
    private fun rotate(v:V):V { val x=v.x*cos(angle)+v.z*sin(angle);val z=-v.x*sin(angle)+v.z*cos(angle)
        return V(x,v.y*cos(.35f)-z*sin(.35f),v.y*sin(.35f)+z*cos(.35f)) }
    private fun paint(draw:GLDraw,vertices:List<V>,h:Float,l:Float,filled:Boolean) {
        val clipped=HyperbolicMesh.clipFront(vertices);if(clipped.size<3)return
        val p=FloatArray(clipped.size*2)
        for(i in clipped.indices) { val v=clipped[i];val s=minOf(draw.W,draw.H)*.36f*3.2f/(3.2f-v.z)
            p[i*2]=draw.W*.5f+v.x*s;p[i*2+1]=draw.H*.5f+v.y*s }
        val c=GLDraw.hsl(((h%1f)+1f)%1f,.95f,l.coerceIn(0f,1f))
        if(filled)draw.polygon(p,c[0],c[1],c[2],1f,true) else for(i in clipped.indices){val j=(i+1)%clipped.size;draw.antiAliasedLine(p[i*2],p[i*2+1],p[j*2],p[j*2+1],c[0],c[1],c[2])}
    }
    override fun draw(draw:GLDraw,audio:AudioData,tick:Int) {
        val r=prepare(draw,audio);val mid=r.motion[1]
        time+=(.013f+mid*.002f)*r.step;angle+=(.008f+mid*.002f)*r.step*r.motionScale
        hue=(hue+(.0025f+mid*.0008f)*r.step)%1f
        draw.rect(0f,0f,draw.W.toFloat(),draw.H.toFloat(),2f/255f,1f/255f,8f/255f,1f)
        draw.circle(draw.W*.5f,draw.H*.5f,minOf(draw.W,draw.H)*.36f*3.2f/sqrt(3.2f*3.2f-1f),.035f,.055f,.12f,1f,true,96)
        val rotated=tiles.map { tile -> tile.map(::rotate) }
        for(index in tiles.indices.sortedBy { i -> rotated[i].map { it.z }.average() }) {
            val tile=tiles[index];val center=centers[index]
            val phase=time+atan2(center.y,center.x)*3f+center.z*4f
            val target=(.075f*sin(phase)+.035f*sin(time*1.4f+index*.52f)+r.motion[0]*.035f*(.5f+.5f*sin(phase+time*.3f))).coerceIn(-.10f,.18f)
            heights[index]=r.smooth(heights[index],target,.08f)
            val base=rotated[index];val visible=HyperbolicMesh.clipFront(base);if(visible.size<3)continue
            val height=heights[index];val axis=(tile[1]-tile[0]).normal();val pivot=(tile[0]+tile[1])*.5f
            val a=.30f*maxOf(0f,sin(phase));val ca=cos(a);val sa=sin(a)
            val cap=tile.map { v -> val rel=v-pivot;rotate((rel*ca+axis.cross(rel)*sa+axis*(rel.dot(axis)*(1f-ca))+pivot)*(1f+height)) }
            val depth=visible.map { it.z }.average().toFloat();val h=hue+index*.033f+.20f*sin(phase)+height*.8f
            for(i in base.indices) { val j=(i+1)%base.size;paint(draw,listOf(base[i],base[j],cap[j],cap[i]),h+.10f,.04f+maxOf(0f,height)*.5f+(i%3)*.018f,true) }
            paint(draw,cap,h,minOf(.48f,.13f+depth*.12f+maxOf(0f,height)*.65f+r.light[0]*.05f*r.accent),true)
            paint(draw,cap,h+.08f,minOf(.90f,.42f+depth*.22f+r.light[2]*.10f+maxOf(0f,height)),false)
        }
    }
}
