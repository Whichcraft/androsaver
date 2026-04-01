package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * TriFluxMode — triangle mosaic wall.
 * All triangles are wireframe with rainbow edges. N_FILLED tiles are filled
 * at any time. On beats, a tile pops to the FRONT at 4-5× size, pulses with
 * bass, then after a fixed lifetime springs back into the grid. Active tiles
 * are drawn last (always on top) with a solid fill.
 * Two independent rainbow sweeps traverse the grid at all times.
 * Port of psysuals `Attractor` class (v2.0.0).
 */
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
        var life   = 0          // frames remaining as active; 0 = not active
        var homeCx = cx0; var homeCy = cy0
        var cvx    = 0f;  var cvy    = 0f
    }

    private class Sweep(var pos: Float, var angle: Float, val vel: Float)

    private val tiles       = ArrayList<Tile>(512)
    private val filledIds   = ArrayList<Int>(N_FILLED)
    private val activeIds   = ArrayList<Int>(N_ACTIVE_MAX)
    private var hue         = 0f
    private var built       = false
    private var swapCd      = 0
    private var autoCd      = (180 + Math.random() * 120).toInt()
    private val sweeps      = arrayOf(
        Sweep(pos = 0f,   angle = (Math.random() * TAU).toFloat(), vel = 3.2f),
        Sweep(pos = 600f, angle = (Math.random() * TAU).toFloat(), vel = 4.1f)
    )
    private var sweepDiag   = 0f
    private var sweepWidth  = 90f

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
    private fun rainbowEdges(draw: GLDraw, pts: FloatArray, alpha: Float = 1f) {
        for (i in 0 until 3) {
            val ax = pts[i * 2]; val ay = pts[i * 2 + 1]
            val bi = (i + 1) % 3
            val bx = pts[bi * 2]; val by = pts[bi * 2 + 1]
            val edgeAngle = atan2(by - ay, bx - ax)
            val h = (hue * 2f + edgeAngle / TAU + 0.5f) % 1f
            val c = GLDraw.hsl(h, 1f, 0.75f)
            draw.line(ax, ay, bx, by, c[0], c[1], c[2], alpha)
        }
    }

    // ── BaseMode overrides ─────────────────────────────────────────────────────

    override fun reset() {
        tiles.clear(); filledIds.clear(); activeIds.clear()
        hue = 0f; built = false; swapCd = 0
        autoCd = (180 + Math.random() * 120).toInt()
        sweeps[0].pos = 0f;   sweeps[0].angle = (Math.random() * TAU).toFloat()
        sweeps[1].pos = 600f; sweeps[1].angle = (Math.random() * TAU).toFloat()
        sweepDiag = 0f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W; val H = draw.H

        if (!built) buildGrid(W, H)

        draw.fadeBlack(28f / 255f)

        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6).coerceIn(0f, 1f)

        hue += 0.003f

        // ── Rotate filled set slowly ──────────────────────────────────────────
        swapCd--
        if (swapCd <= 0) {
            swapCd = 70 + (Math.random() * 50).toInt()
            val vis        = visibleIndices(W, H)
            val candidates = vis.filter { it !in filledIds }
            if (candidates.isNotEmpty() && filledIds.isNotEmpty()) {
                val slot = (Math.random() * filledIds.size).toInt()
                filledIds[slot] = candidates[(Math.random() * candidates.size).toInt()]
            }
        }

        // ── Bass beat: pop an interior tile to front ──────────────────────────
        val bassBeat = beat > 0.2f && bass > 0.25f
        if (bassBeat) {
            if (activeIds.size < N_ACTIVE_MAX) {
                val candidates = interiorIndices(W, H).filter { it !in activeIds }
                if (candidates.isNotEmpty()) {
                    val idx = candidates[(Math.random() * candidates.size).toInt()]
                    activeIds.add(idx)
                    val t = tiles[idx]
                    t.life   = ACTIVE_LIFE_MIN + (Math.random() * (ACTIVE_LIFE_MAX - ACTIVE_LIFE_MIN)).toInt()
                    t.rotVel = (if (Math.random() < 0.5) -1f else 1f) *
                               (0.04f + Math.random().toFloat() * 0.06f)
                    t.cvx = 0f; t.cvy = 0f
                }
            } else {
                for (idx in activeIds) {
                    if (tiles[idx].life < MIN_LIFE) tiles[idx].life = MIN_LIFE
                }
            }
        }

        // ── Periodic auto-activation ──────────────────────────────────────────
        autoCd--
        if (autoCd <= 0) {
            autoCd = 180 + (Math.random() * 140).toInt()
            val candidates = interiorIndices(W, H).filter { it !in activeIds }.shuffled()
            val nNew = 1 + (Math.random() * 2).toInt()
            for (idx in candidates.take(nNew)) {
                if (activeIds.size < N_ACTIVE_MAX) {
                    activeIds.add(idx)
                    val t = tiles[idx]
                    t.life   = ACTIVE_LIFE_MIN + (Math.random() * (ACTIVE_LIFE_MAX - ACTIVE_LIFE_MIN)).toInt()
                    t.rotVel = (if (Math.random() < 0.5) -1f else 1f) *
                               (0.03f + Math.random().toFloat() * 0.05f)
                    t.cvx = 0f; t.cvy = 0f
                }
            }
        }

        // ── Advance sweeps ────────────────────────────────────────────────────
        for (sw in sweeps) {
            sw.pos += sw.vel
            if (sw.pos > sweepDiag + sweepWidth) {
                sw.pos   = -sweepWidth
                sw.angle = (Math.random() * TAU).toFloat()
            }
        }

        // ── Pass 1: non-active tiles ──────────────────────────────────────────
        for (i in tiles.indices) {
            if (i in activeIds) continue
            val tile = tiles[i]

            tile.hue = (tile.hue + tile.hvel + 0.00015f) % 1f

            // Spring back toward scale=1 and rot=0
            tile.svel   += (1f - tile.scale) * 0.15f; tile.svel   *= 0.75f; tile.scale += tile.svel
            tile.rotVel += (0f - tile.rot)   * 0.05f; tile.rotVel *= 0.88f; tile.rot   += tile.rotVel

            val pts = screenVerts(tile)
            val minX = minOf(pts[0], pts[2], pts[4])
            val maxX = maxOf(pts[0], pts[2], pts[4])
            val minY = minOf(pts[1], pts[3], pts[5])
            val maxY = maxOf(pts[1], pts[3], pts[5])
            if (maxX < 0 || minX >= W || maxY < 0 || minY >= H) continue

            // Sweep glows — drawn first so tile fill + edges render on top
            for (sw in sweeps) {
                val swCos = cos(sw.angle); val swSin = sin(sw.angle)
                val d     = tile.cx * swCos + tile.cy * swSin
                val dist  = abs(d - sw.pos)
                if (dist < sweepWidth) {
                    val swT    = 1f - dist / sweepWidth
                    val sweepH  = (hue + d / sweepDiag) % 1f
                    val sweepBr = 0.20f + swT * 0.55f
                    val sc = GLDraw.hsl(sweepH, 1f, sweepBr)
                    draw.polygon(pts, sc[0], sc[1], sc[2], (swT * 0.85f).coerceIn(0f, 1f), filled = true)
                }
            }

            if (i in filledIds) {
                val h      = (tile.hue + hue) % 1f
                val bright = minOf(tile.bright + bass * 0.20f, 0.72f)
                val fc = GLDraw.hsl(h, 1f, bright)
                draw.polygon(pts, fc[0], fc[1], fc[2], 0.85f, filled = true)
            }

            rainbowEdges(draw, pts)
        }

        // ── Pass 2: active tiles — on top, bass-pulsing ───────────────────────
        val finished = ArrayList<Int>()
        for (i in activeIds) {
            val tile = tiles[i]
            tile.hue = (tile.hue + tile.hvel + 0.00015f) % 1f

            val alive = tile.life > 0
            if (alive) {
                tile.life--
                val target    = 4.5f + bass * 4.0f
                tile.svel    += (target - tile.scale) * 0.22f; tile.svel *= 0.70f
                tile.scale    = minOf(tile.scale + tile.svel, 12f)
                tile.rot     += tile.rotVel
                tile.rotVel  += bass * 0.016f * if (tile.rotVel >= 0) 1f else -1f
                tile.rotVel  *= 0.96f
                tile.cx += tile.cvx; tile.cy += tile.cvy
                tile.cvx *= 0.97f;   tile.cvy *= 0.97f
            } else {
                // Spring back to rest
                tile.svel    += (1f - tile.scale) * 0.12f; tile.svel *= 0.78f; tile.scale += tile.svel
                tile.rotVel  += (0f - tile.rot)   * 0.06f; tile.rotVel *= 0.85f; tile.rot += tile.rotVel
                tile.cvx *= 0.80f; tile.cvy *= 0.80f
                tile.cx  += (tile.homeCx - tile.cx) * 0.10f
                tile.cy  += (tile.homeCy - tile.cy) * 0.10f
                if (abs(tile.scale - 1f) < 0.03f && abs(tile.rot) < 0.02f
                        && abs(tile.rotVel) < 0.003f
                        && abs(tile.cx - tile.homeCx) < 1f
                        && abs(tile.cy - tile.homeCy) < 1f) {
                    tile.scale = 1f; tile.rot = 0f; tile.rotVel = 0f; tile.svel = 0f
                    tile.cx = tile.homeCx; tile.cy = tile.homeCy
                    finished.add(i)
                }
            }

            var pts = screenVerts(tile)

            // Bounce off screen edges (active phase only)
            if (alive) {
                val minX = minOf(pts[0], pts[2], pts[4])
                val maxX = maxOf(pts[0], pts[2], pts[4])
                val minY = minOf(pts[1], pts[3], pts[5])
                val maxY = maxOf(pts[1], pts[3], pts[5])
                if (minX < 0)   { tile.cvx = abs(tile.cvx) * 0.8f + 1.5f; tile.cx -= minX;       pts = screenVerts(tile) }
                else if (maxX > W) { tile.cvx = -(abs(tile.cvx) * 0.8f + 1.5f); tile.cx -= (maxX - W); pts = screenVerts(tile) }
                if (minY < 0)   { tile.cvy = abs(tile.cvy) * 0.8f + 1.5f; tile.cy -= minY;       pts = screenVerts(tile) }
                else if (maxY > H) { tile.cvy = -(abs(tile.cvy) * 0.8f + 1.5f); tile.cy -= (maxY - H); pts = screenVerts(tile) }
            }

            val h      = (tile.hue + hue) % 1f
            val bright = minOf(tile.bright + bass * 0.40f + 0.25f, 0.92f)

            // Solid black backing so nothing bleeds through
            draw.polygon(pts, 0f, 0f, 0f, 1f, filled = true)
            // Colour fill
            val fc = GLDraw.hsl(h, 1f, bright)
            draw.polygon(pts, fc[0], fc[1], fc[2], 1f, filled = true)
            // Bold rainbow edges
            rainbowEdges(draw, pts)
        }

        for (i in finished) activeIds.remove(i)
    }
}
