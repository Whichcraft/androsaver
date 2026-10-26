package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** psysuals 3.20 living graph: gradual parent-linked births and oldest-node retirement; surviving edge signals are remapped rather than discarded. */
class SynapseMode : BaseMode() {

    override val name = "Synapse"

    private companion object {
        const val INITIAL_NODES = 55
        const val MIN_NODES = 28
        const val MAX_NODES = 90
        const val EDGES_PER_NODE = 3
        const val MAX_SIGNALS = 240
        val TAU = (2.0 * PI).toFloat()
    }

    private data class Signal(val edgeIdx: Int, var t: Float, val spd: Float, val hue: Float)

    private val nodeX = ArrayList<Float>(MAX_NODES)
    private val nodeY = ArrayList<Float>(MAX_NODES)
    private val anchorX = ArrayList<Float>(MAX_NODES)
    private val anchorY = ArrayList<Float>(MAX_NODES)
    private val wanderPhase = ArrayList<Float>(MAX_NODES)
    private val wanderSpeed = ArrayList<Float>(MAX_NODES)
    private val wanderAmp = ArrayList<Float>(MAX_NODES)
    private val edges = ArrayList<IntArray>(MAX_NODES * EDGES_PER_NODE)
    private val outgoing = ArrayList<ArrayList<Int>>(MAX_NODES)
    private val glow = ArrayList<Float>(MAX_NODES)
    private val signals = ArrayList<Signal>(MAX_SIGNALS)

    private val visibility=ArrayList<Float>(MAX_NODES)
    private val originX=ArrayList<Float>(MAX_NODES);private val originY=ArrayList<Float>(MAX_NODES)
    private var retiring=false
    private var nNodes = INITIAL_NODES
    private var hue = 0f
    private var beatPrev = 0f
    private var autoFireCd = 30f
    private var topologyCd = 120f
    private var initialized = false
    private var lastW = 0f
    private var lastH = 0f

    override fun reset() {
        nNodes = INITIAL_NODES
        visibility.clear();originX.clear();originY.clear();retiring=false
        nodeX.clear()
        nodeY.clear()
        anchorX.clear()
        anchorY.clear()
        wanderPhase.clear()
        wanderSpeed.clear()
        wanderAmp.clear()
        edges.clear()
        outgoing.clear()
        glow.clear()
        signals.clear()
        hue = 0f
        beatPrev = 0f
        autoFireCd = 30f
        topologyCd = 120f
        initialized = false
        lastW = 0f
        lastH = 0f
    }

    private fun randomNode(W: Float, H: Float): Pair<Float, Float> {
        val ang = Math.random().toFloat() * TAU
        val r = (0.10f + Math.random().toFloat() * 0.34f) * minOf(W, H)
        val x = W / 2f + cos(ang) * r * (0.80f + Math.random().toFloat() * 0.40f)
        val y = H / 2f + sin(ang) * r * (0.60f + Math.random().toFloat() * 0.30f)
        return x to y
    }

    private fun addNode(W: Float, H: Float) {
        val parent=if(initialized && nodeX.isNotEmpty()) {
            val candidates=nodeX.indices.filter{visibility[it]>=.9f}.ifEmpty{nodeX.indices.toList()}
            var credit=Math.random().toFloat()*candidates.sumOf{(.1f+glow[it]).toDouble()}.toFloat()
            candidates.firstOrNull{credit-=.1f+glow[it];credit<=0f}?:candidates.last()
        } else -1
        val origin=if(parent>=0) nodeX[parent] to nodeY[parent] else randomNode(W,H)
        val a=atan2(origin.second-H*.5f,origin.first-W*.5f)+(Math.random().toFloat()*2.4f-1.2f)
        val distance=(.05f+Math.random().toFloat()*.06f)*minOf(W,H)
        val x=if(parent>=0)(origin.first+cos(a)*distance).coerceIn(W*.06f,W*.94f) else origin.first
        val y=if(parent>=0)(origin.second+sin(a)*distance).coerceIn(H*.06f,H*.94f) else origin.second
        visibility.add(if(parent>=0)0f else 1f);originX.add(origin.first);originY.add(origin.second)
        nodeX.add(x)
        nodeY.add(y)
        anchorX.add(x)
        anchorY.add(y)
        wanderPhase.add(Math.random().toFloat() * TAU)
        wanderSpeed.add(0.006f + Math.random().toFloat() * 0.012f)
        wanderAmp.add((0.012f + Math.random().toFloat() * 0.033f) * minOf(W, H))
        glow.add(0f)
        outgoing.add(ArrayList())
        nNodes = nodeX.size
    }

    private fun rebuildEdges(removed:Int?=null) {
        val active=signals.mapNotNull{signal -> edges.getOrNull(signal.edgeIdx)?.let{edge ->
            if(removed!=null && (edge[0]==removed || edge[1]==removed))null else {
                val a=edge[0]-if(removed!=null&&edge[0]>removed)1 else 0
                val b=edge[1]-if(removed!=null&&edge[1]>removed)1 else 0
                Triple(a,b,signal)
            }
        }}
        edges.clear()
        outgoing.clear()
        repeat(nNodes) { outgoing.add(ArrayList()) }

        for (i in 0 until nNodes) {
            val nearest = (0 until nNodes)
                .filter { it != i }
                .sortedBy { hypot(nodeX[it] - nodeX[i], nodeY[it] - nodeY[i]) }
            for (j in nearest.take(EDGES_PER_NODE)) {
                edges.add(intArrayOf(i, j))
            }
        }
        for ((edgeIndex, edge) in edges.withIndex()) {
            outgoing[edge[0]].add(edgeIndex)
        }

        val lookup=edges.withIndex().associate{(i,edge)-> (edge[0] to edge[1]) to i}
        signals.clear()
        for((a,b,signal) in active) lookup[a to b]?.let{signals.add(signal.copy(edgeIdx=it))}
    }

    private fun init(W: Float, H: Float) {
        visibility.clear();originX.clear();originY.clear();retiring=false
        nodeX.clear()
        nodeY.clear()
        anchorX.clear()
        anchorY.clear()
        wanderPhase.clear()
        wanderSpeed.clear()
        wanderAmp.clear()
        glow.clear()
        outgoing.clear()
        repeat(nNodes) { addNode(W, H) }
        rebuildEdges()
        lastW = W
        lastH = H
        initialized = true
    }

    private fun mutateTopology(W: Float, H: Float, add: Boolean) {
        when {
            add && nNodes < MAX_NODES -> addNode(W, H)
            !add && nNodes > MIN_NODES -> {
                val idx = 0
                visibility.removeAt(idx);originX.removeAt(idx);originY.removeAt(idx)
                nodeX.removeAt(idx)
                nodeY.removeAt(idx)
                anchorX.removeAt(idx)
                anchorY.removeAt(idx)
                wanderPhase.removeAt(idx)
                wanderSpeed.removeAt(idx)
                wanderAmp.removeAt(idx)
                glow.removeAt(idx)
                nNodes = nodeX.size
            }
            else -> return
        }
        rebuildEdges(if(add)null else 0)
    }

    private fun fire(idx: Int, fanout: Int = 2) {
        if (idx !in 0 until nNodes || visibility[idx]<.9f || (idx==0 && retiring)) return
        glow[idx] = 1f
        val h = (hue + idx.toFloat() / nNodes.coerceAtLeast(1) * 0.4f) % 1f
        val outList = outgoing.getOrNull(idx) ?: return
        if (outList.isEmpty() || signals.size >= MAX_SIGNALS) return
        val count = fanout.coerceIn(1, outList.size)
        for (edgeIndex in outList.shuffled().take(count)) {
            if (signals.size >= MAX_SIGNALS) break
            signals.add(Signal(edgeIndex, 0f, 0.020f + Math.random().toFloat() * 0.010f, h))
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.coerceAtLeast(1).toFloat()
        val H = draw.H.coerceAtLeast(1).toFloat()
        val response=prepare(draw,audio);val step=response.step
        val bass=response.motion[0];val mid=response.motion[1];val high=response.motion[2]
        if(!initialized) init(W,H) else if(W!=lastW || H!=lastH) {
            val sx=W/lastW;val sy=H/lastH
            for(i in nodeX.indices){nodeX[i]*=sx;nodeY[i]*=sy;anchorX[i]*=sx;anchorY[i]*=sy;originX[i]*=sx;originY[i]*=sy;wanderAmp[i]*=minOf(sx,sy)}
            lastW=W;lastH=H
        }

        hue=(hue+(.003f+mid*.002f)*step)%1f
        for(i in nodeX.indices) visibility[i]=(visibility[i]+if(i==0&&retiring)-step/60f else step/75f).coerceIn(0f,1f)
        if(retiring && visibility[0]<=1e-8f) {
            mutateTopology(W,H,false);retiring=false;mutateTopology(W,H,true);mutateTopology(W,H,true)
        }
        topologyCd-=step
        if(topologyCd<=0f && !retiring){retiring=true;topologyCd+=180f}

        val minX = minOf(4f, W / 2f)
        val maxX = maxOf(minX, W - minX)
        val minY = minOf(4f, H / 2f)
        val maxY = maxOf(minY, H - minY)
        for (i in 0 until nNodes) {
            wanderPhase[i] += wanderSpeed[i] * (1f + high * .25f + bass * .1f)*step*response.motionScale
            val phase = wanderPhase[i]
            nodeX[i] = (anchorX[i] + sin(phase) * wanderAmp[i]).coerceIn(minX, maxX)
            nodeY[i] = (anchorY[i] + cos(phase * 0.77f) * wanderAmp[i] * 0.72f)
                .coerceIn(minY, maxY)
            if(visibility[i]<1f && !(i==0 && retiring)){
                val t=visibility[i];val ease=t*t*(3f-2f*t)
                nodeX[i]=originX[i]+(nodeX[i]-originX[i])*ease;nodeY[i]=originY[i]+(nodeY[i]-originY[i])*ease
            }
        }

        if (audio.beat > 0.65f && beatPrev <= 0.65f) {
            repeat((1 + bass * 3).toInt()) {
                val idx = (Math.random() * nNodes).toInt().coerceIn(0, nNodes - 1)
                fire(idx, fanout = 2 + (high * 2f).toInt())
            }
        }
        beatPrev = audio.beat

        autoFireCd-=step
        if (autoFireCd <= 0) {
            val idx = (Math.random() * nNodes).toInt().coerceIn(0, nNodes - 1)
            fire(idx, fanout = 1)
            autoFireCd = maxOf(8f,25f-mid*15f)
        }

        val spdMul = 1f + bass * 2f
        val liveSignals = ArrayList<Signal>(signals.size)
        val firedNodes = ArrayList<Int>()
        for (signal in signals) {
            val edge = edges.getOrNull(signal.edgeIdx) ?: continue
            signal.t += signal.spd * spdMul*step
            if (signal.t < 1f) liveSignals.add(signal) else firedNodes.add(edge[1])
        }
        signals.clear()
        signals.addAll(liveSignals)
        for (idx in firedNodes.take(18)) fire(idx, fanout = 1 + (mid * 2f).toInt())

        for (i in 0 until nNodes) glow[i] = maxOf(0f, glow[i] - 0.035f*step)

        draw.fadeBlackTimed(18f / 255f,step)

        for (edge in edges) {
            val from = edge[0]
            val h = (hue + from.toFloat() / nNodes * 0.3f) % 1f
            val vis=minOf(visibility[from],visibility[edge[1]])
            val c = GLDraw.hsl(h, l = (0.06f + glow[from] * 0.14f)*vis)
            draw.line(nodeX[from], nodeY[from], nodeX[from]+(nodeX[edge[1]]-nodeX[from])*vis, nodeY[from]+(nodeY[edge[1]]-nodeY[from])*vis,
                c[0], c[1], c[2], c[3])
        }

        draw.setAdditiveBlend()
        for (signal in signals) {
            val edge = edges.getOrNull(signal.edgeIdx) ?: continue
            val sx = nodeX[edge[0]] + (nodeX[edge[1]] - nodeX[edge[0]]) * signal.t
            val sy = nodeY[edge[0]] + (nodeY[edge[1]] - nodeY[edge[0]]) * signal.t
            val bright = minOf(.95f,.55f+response.light[0]*.2f*response.accent+response.light[2]*.1f)*minOf(visibility[edge[0]],visibility[edge[1]])
            val radius = maxOf(2f, 3f + bass * 2f)
            val glowColor = GLDraw.hsl(signal.hue, l = bright * 0.30f)
            draw.circle(sx, sy, radius + 3f, glowColor[0], glowColor[1], glowColor[2],
                glowColor[3], filled = true, segments = 8)
            val color = GLDraw.hsl(signal.hue, l = bright)
            draw.circle(sx, sy, radius, color[0], color[1], color[2], color[3],
                filled = true, segments = 8)
        }

        for (i in 0 until nNodes) {
            val g = glow[i]*visibility[i]
            val h = (hue + i.toFloat() / nNodes * 0.4f) % 1f
            if (g > 0.05f) {
                val radius = maxOf(2f, 4f + g * 8f + bass * 4f)
                val glowColor = GLDraw.hsl(h, l = g * 0.22f)
                draw.circle(nodeX[i], nodeY[i], radius + 3f, glowColor[0], glowColor[1],
                    glowColor[2], glowColor[3], filled = true, segments = 8)
                val color = GLDraw.hsl(h, l = g * 0.70f)
                draw.circle(nodeX[i], nodeY[i], radius, color[0], color[1], color[2],
                    color[3], filled = true, segments = 8)
            } else {
                val color = GLDraw.hsl(h, l = 0.10f*visibility[i])
                draw.circle(nodeX[i], nodeY[i], 2f, color[0], color[1], color[2],
                    color[3], filled = true, segments = 6)
            }
        }
        draw.setNormalBlend()
    }
}
