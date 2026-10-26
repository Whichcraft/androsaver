package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** psysuals 3.20 triangle mosaic: beat-edge activation, elapsed-time lifetimes, substepped springs, stable resize and antialiased rainbow outlines. */
class TriFluxMode : BaseMode() {

    override val name = "TriFlux"

    private companion object {
        const val N_COLS        = 14
        const val GAP           = 0.88f
        const val N_FILLED      = 5
        const val N_ACTIVE_MAX  = 3
        const val ACTIVE_LIFE_MIN = 150
        const val ACTIVE_LIFE_MAX = 360
        const val MIN_LIFE      = 150
        const val TAU           = (Math.PI * 2).toFloat()
    }

    private class Tile(
        val vertsBase: Array<FloatArray>,  // [3][2] original grid positions
        val cx0: Float, val cy0: Float,    // immutable grid centre
        val hvel: Float,
        val bright0: Float
    ) {
        var hue    = Math.random().toFloat()
        var bright = bright0
        var cx     = cx0; var cy = cy0
        var rot    = 0f;  var rotVel = 0f
        var scale  = 1f;  var svel   = 0f
        var life   = 0f          // frames remaining as active; 0 = not active
        var homeCx = cx0; var homeCy = cy0
        var cvx    = 0f;  var cvy    = 0f
    }

    private class Sweep(var pos: Float, var angle: Float, val vel: Float)

    private val tiles       = ArrayList<Tile>(512)
    private val filledIds   = ArrayList<Int>(N_FILLED)
    private val activeIds   = ArrayList<Int>(N_ACTIVE_MAX)
    private var hue         = 0f
    private var built       = false
    private var swapCd      = 0f
    private var autoCd      = (180 + Math.random() * 120).toFloat()
    private var beatPrev=0f;private var beatCd=0f
    private val sweeps      = arrayOf(
        Sweep(pos = 0f,   angle = (Math.random() * TAU).toFloat(), vel = 3.2f),
        Sweep(pos = 600f, angle = (Math.random() * TAU).toFloat(), vel = 4.1f)
    )
    private var sweepDiag   = 0f
    private var sweepWidth  = 90f
    private var lastW       = 0
    private var lastH       = 0

    // ── Build grid ─────────────────────────────────────────────────────────────

    private fun buildGrid(W: Int, H: Int) {
        tiles.clear()
        val tw       = W.toFloat() / N_COLS
        val th       = tw * sqrt(3f) / 2f
        // Extend one tile past every edge so no clipped triangles appear at borders
        val nColsExt = N_COLS + 2
        val nRows    = (H / th).toInt() + 3
        val x0       = -tw; val y0 = -th

        for (r in 0 until nRows) {
            val yTop = y0 + r * th; val yBot = yTop + th
            for (k in 0..nColsExt) {
                val x = x0 + k * tw
                // Up-pointing triangle
                val upV = arrayOf(
                    floatArrayOf(x + tw / 2f, yTop),
                    floatArrayOf(x,            yBot),
                    floatArrayOf(x + tw,       yBot)
                )
                tiles.add(makeTile(upV))
                // Down-pointing triangle
                val dnV = arrayOf(
                    floatArrayOf(x + tw / 2f,       yTop),
                    floatArrayOf(x + tw + tw / 2f,  yTop),
                    floatArrayOf(x + tw,             yBot)
                )
                tiles.add(makeTile(dnV))
            }
        }

        val vis = visibleIndices(W, H)
        filledIds.clear()
        val shuffled = vis.shuffled()
        for (i in 0 until minOf(N_FILLED, shuffled.size)) filledIds.add(shuffled[i])
        built = true
        sweepDiag  = hypot(W.toFloat(), H.toFloat())
        sweepWidth = sweepDiag * 0.13f  // ~13% of diagonal width
        for (sw in sweeps) if (sw.pos > sweepDiag) sw.pos = -sweepWidth
    }

    private fun makeTile(verts: Array<FloatArray>): Tile {
        val cx = (verts[0][0] + verts[1][0] + verts[2][0]) / 3f
        val cy = (verts[0][1] + verts[1][1] + verts[2][1]) / 3f
        return Tile(
            vertsBase = verts,
            cx0       = cx, cy0 = cy,
            hvel      = (Math.random().toFloat() * 0.0012f - 0.0006f),
            bright0   = 0.20f + Math.random().toFloat() * 0.30f
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun visibleIndices(W: Int, H: Int): List<Int> {
        val margin = W.toFloat() / N_COLS
        return tiles.indices.filter { i ->
            val t = tiles[i]
            t.cx >= -margin && t.cx < W + margin && t.cy >= -margin && t.cy < H + margin
        }
    }

    private fun interiorIndices(W: Int, H: Int): List<Int> {
        val margin = W.toFloat() / N_COLS * 1.5f
        return tiles.indices.filter { i ->
            val t = tiles[i]
            t.cx >= margin && t.cx < W - margin && t.cy >= margin && t.cy < H - margin
        }
    }

    /** Compute screen-space triangle vertices applying scale + rotation around centroid. */
    private fun screenVerts(tile: Tile): FloatArray {
        val s    = GAP * tile.scale
        val cosR = cos(tile.rot); val sinR = sin(tile.rot)
        val out  = FloatArray(6)
        for (i in 0 until 3) {
            val dx = (tile.vertsBase[i][0] - tile.cx0) * s
            val dy = (tile.vertsBase[i][1] - tile.cy0) * s
            out[i * 2]     = tile.cx + dx * cosR - dy * sinR
            out[i * 2 + 1] = tile.cy + dx * sinR + dy * cosR
        }
        return out
    }

    /** Draw each edge of a triangle with its own hue. */
    private fun rainbowEdges(draw: GLDraw, pts: FloatArray, alpha: Float = 1f, width:Float=1f) {
        for (i in 0 until 3) {
            val ax = pts[i * 2]; val ay = pts[i * 2 + 1]
            val bi = (i + 1) % 3
            val bx = pts[bi * 2]; val by = pts[bi * 2 + 1]
            val edgeAngle = atan2(by - ay, bx - ax)
            val h = (hue * 2f + edgeAngle / TAU + 0.5f) % 1f
            val c = GLDraw.hsl(h, 1f, 0.75f)
            draw.antiAliasedLine(ax, ay, bx, by, c[0], c[1], c[2], alpha,width)
        }
    }

    // ── BaseMode overrides ─────────────────────────────────────────────────────

    override fun reset() {
        tiles.clear(); filledIds.clear(); activeIds.clear()
        hue = 0f; built = false; swapCd = 0f;beatPrev=0f;beatCd=0f
        autoCd = (180 + Math.random() * 120).toFloat()
        sweeps[0].pos = 0f;   sweeps[0].angle = (Math.random() * TAU).toFloat()
        sweeps[1].pos = 600f; sweeps[1].angle = (Math.random() * TAU).toFloat()
        sweepDiag = 0f
        lastW = 0
        lastH = 0
    }

    private fun sweepRange(angle:Float,W:Int,H:Int):Pair<Float,Float> {
        val c=cos(angle);val s=sin(angle);val values=floatArrayOf(0f,W*c,H*s,W*c+H*s)
        return values.min() to values.max()
    }
    private fun activate(W:Int,H:Int) {
        val candidates=interiorIndices(W,H).filter{it !in activeIds}
        if(candidates.isEmpty() || activeIds.size>=N_ACTIVE_MAX)return
        val i=candidates.random();val tile=tiles[i];activeIds+=i
        tile.life=ACTIVE_LIFE_MIN+(Math.random().toFloat()*(ACTIVE_LIFE_MAX-ACTIVE_LIFE_MIN))
        tile.rotVel=(if(Math.random()<.5)-1f else 1f)*(.008f+Math.random().toFloat()*.010f)
    }
    override fun draw(draw:GLDraw,audio:AudioData,tick:Int) {
        val W=draw.W;val H=draw.H
        val response=prepare(draw,audio);val step=response.step
        val bass=response.motion[0];val mid=response.motion[1];val high=response.motion[2]
        if(!built || W!=lastW || H!=lastH) {
            val previous=activeIds.map{tiles[it] to (tiles[it].cx/lastW.coerceAtLeast(1) to tiles[it].cy/lastH.coerceAtLeast(1))}
            activeIds.clear();buildGrid(W,H);lastW=W;lastH=H;sweepWidth=90f
            for((old,pos) in previous){
                val i=interiorIndices(W,H).filter{it !in activeIds}.minByOrNull{val t=tiles[it];(t.cx-pos.first*W).pow(2)+(t.cy-pos.second*H).pow(2)}?:continue
                val t=tiles[i];t.hue=old.hue;t.rot=old.rot;t.rotVel=old.rotVel;t.scale=old.scale;t.svel=old.svel;t.life=old.life;activeIds+=i
            }
            for((i,sw) in sweeps.withIndex()){val range=sweepRange(sw.angle,W,H);sw.pos=range.first-sweepWidth+i*(range.second-range.first+sweepWidth)*.5f}
        }
        draw.fadeBlackTimed(48f/255f,step)
        if(audio.beat>.25f && beatPrev<=.25f && beatCd<=0f){activate(W,H);beatCd=20f}
        beatPrev=audio.beat;beatCd=maxOf(0f,beatCd-step)
        hue+=.003f*step;swapCd-=step;autoCd-=step
        if(swapCd<=0f){swapCd+=(70+Math.random()*50).toFloat();val choices=visibleIndices(W,H).filter{it !in filledIds};if(choices.isNotEmpty()&&filledIds.isNotEmpty())filledIds[(Math.random()*filledIds.size).toInt()]=choices.random()}
        if(autoCd<=0f){autoCd+=(180+Math.random()*140).toFloat();activate(W,H)}
        for(sw in sweeps){sw.pos+=sw.vel*(1f+mid*.35f)*step;if(sw.pos>sweepRange(sw.angle,W,H).second+sweepWidth){sw.angle=(Math.random()*TAU).toFloat();sw.pos=sweepRange(sw.angle,W,H).first-sweepWidth}}
        val substeps=maxOf(1,ceil(step*2f).toInt());val dt=step/substeps
        val finished=mutableListOf<Int>()
        for((i,t) in tiles.withIndex()){
            t.hue=(t.hue+(t.hvel+.00015f)*step)%1f
            val active=i in activeIds;if(active)t.life=maxOf(0f,t.life-step)
            val alive=active&&t.life>0f;val target=if(alive)4.5f+bass*1.2f+mid*.4f else 1f
            repeat(substeps){
                t.svel+=(target-t.scale)*.14f*dt;t.svel*=.74f.pow(dt);t.scale+=t.svel*dt
                if(alive){val targetSpeed=(if(t.rotVel<0f)-1f else 1f)*(.010f+bass*.006f+high*.003f);t.rotVel+=(targetSpeed-t.rotVel)*(1f-.96f.pow(dt))}
                else {t.rotVel+=-t.rot*.05f*dt;t.rotVel*=.85f.pow(dt)}
                t.rot+=t.rotVel*dt
            }
            if(active&&!alive&&abs(t.scale-1f)<.03f&&abs(t.rot)<.02f&&abs(t.rotVel)<.003f){t.scale=1f;t.rot=0f;t.rotVel=0f;t.svel=0f;finished+=i}
        }
        activeIds.removeAll(finished.toSet())
        for((i,t) in tiles.withIndex()){
            if(i in activeIds)continue
            val points=screenVerts(t)
            for(sw in sweeps){val distance=t.cx*cos(sw.angle)+t.cy*sin(sw.angle);val strength=maxOf(0f,1f-abs(distance-sw.pos)/sweepWidth)
                if(strength>0f){val c=GLDraw.hsl((hue+distance/sweepDiag)%1f,1f,minOf(.85f,.2f+strength*.55f+mid*.1f));draw.polygon(points,c[0],c[1],c[2],1f,true)}}
            if(i in filledIds){val c=GLDraw.hsl((t.hue+hue)%1f,1f,minOf(.75f,t.bright+response.light[0]*.2f));draw.polygon(points,c[0],c[1],c[2],1f,true)}
            rainbowEdges(draw,points)
        }
        for(i in activeIds){val t=tiles[i];val points=screenVerts(t);fitViewport(points,W,H)
            val c=GLDraw.hsl((t.hue+hue)%1f,1f,minOf(.92f,t.bright+.25f+response.light[0]*.25f))
            draw.polygon(points,0f,0f,0f,1f,true);draw.polygon(points,c[0],c[1],c[2],1f,true)
            rainbowEdges(draw,points,width=maxOf(2f,3f+response.light[2]*1.5f))
        }
    }
}
