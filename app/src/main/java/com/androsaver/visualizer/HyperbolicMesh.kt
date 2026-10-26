package com.androsaver.visualizer

import kotlin.math.*

/** Dual of a twice-subdivided icosahedron: the shared pentagon/hexagon mesh from psysuals 3.20. */
internal object HyperbolicMesh {
    data class V(val x:Float,val y:Float,val z:Float) {
        operator fun plus(b:V)=V(x+b.x,y+b.y,z+b.z)
        operator fun minus(b:V)=V(x-b.x,y-b.y,z-b.z)
        operator fun times(s:Float)=V(x*s,y*s,z*s)
        fun dot(b:V)=x*b.x+y*b.y+z*b.z
        fun cross(b:V)=V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
        fun normal()=this*(1f/sqrt(dot(this)).coerceAtLeast(1e-8f))
    }
    fun tiles(): List<List<V>> {
        val p=(1f+sqrt(5f))/2f
        val vertices=mutableListOf(V(-1f,p,0f),V(1f,p,0f),V(-1f,-p,0f),V(1f,-p,0f),V(0f,-1f,p),V(0f,1f,p),V(0f,-1f,-p),V(0f,1f,-p),V(p,0f,-1f),V(p,0f,1f),V(-p,0f,-1f),V(-p,0f,1f)).map { it.normal() }.toMutableList()
        var faces=listOf(0 to (11 to 5),0 to (5 to 1),0 to (1 to 7),0 to (7 to 10),0 to (10 to 11),1 to (5 to 9),5 to (11 to 4),11 to (10 to 2),10 to (7 to 6),7 to (1 to 8),3 to (9 to 4),3 to (4 to 2),3 to (2 to 6),3 to (6 to 8),3 to (8 to 9),4 to (9 to 5),2 to (4 to 11),6 to (2 to 10),8 to (6 to 7),9 to (8 to 1)).map { intArrayOf(it.first,it.second.first,it.second.second) }
        repeat(2) {
            val cache=mutableMapOf<Pair<Int,Int>,Int>()
            fun middle(a:Int,b:Int):Int=cache.getOrPut(minOf(a,b) to maxOf(a,b)) { vertices.add((vertices[a]+vertices[b]).normal()); vertices.lastIndex }
            faces=faces.flatMap { f -> val a=f[0];val b=f[1];val c=f[2];val ab=middle(a,b);val bc=middle(b,c);val ca=middle(c,a)
                listOf(intArrayOf(a,ab,ca),intArrayOf(b,bc,ab),intArrayOf(c,ca,bc),intArrayOf(ab,bc,ca)) }
        }
        val centers=faces.map { (vertices[it[0]]+vertices[it[1]]+vertices[it[2]]).normal() }
        return vertices.mapIndexed { i,v ->
            val tangent=v.cross(if(abs(v.z)<.9f) V(0f,0f,1f) else V(0f,1f,0f)).normal()
            val bitangent=v.cross(tangent)
            faces.indices.filter { i in faces[it] }.sortedBy { atan2(centers[it].dot(bitangent),centers[it].dot(tangent)) }.map { centers[it] }
        }
    }
    fun clipFront(vertices:List<V>):List<V> {
        val horizon=1f/3.2f; val result=mutableListOf<V>()
        for(i in vertices.indices) { val a=vertices[i];val b=vertices[(i+1)%vertices.size];val inside=a.z>=horizon
            if(inside) result+=a
            if(inside!=(b.z>=horizon)) result+=a+(b-a)*((horizon-a.z)/(b.z-a.z)) }
        return result
    }
}
