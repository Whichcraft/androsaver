package com.androsaver.visualizer.modes

import kotlin.math.*
import kotlin.random.Random

/**
 * Mycelium colony model — the psysuals 3.21 hyphal web.
 *
 * The colony is grown, not sampled: three fruiting bodies carry branching
 * filaments whose nodes, edges, tips, packets and spores live in bounded
 * pools. Light packets travel along the filaments and hand over to a
 * neighbouring filament at the junctions; spores drift, settle and germinate
 * into new growth. The model holds no GL state, so its behaviour can be
 * checked without a device.
 */
class MyceliumColony(
    width: Int,
    height: Int,
    seed: Int = 0x4D59,
    lowSpec: Boolean = false
) {
    companion object {
        const val MAX_EDGES = 560
        const val MAX_TIPS = 220
        const val MAX_PULSES = 260
        const val PULSE_TARGET = 48
        const val MAX_SPORES = 160
        const val MAX_NODES = 2400
        const val BODY_COUNT = 3
        const val MAX_DEPTH = 14
        const val EDGE_LIFETIME = 1500f
        const val MAX_RINGS = 8
        const val ROOT_TIPS_PER_BODY = 4
        const val WARMUP_STEPS = 45
        const val PULSE_COOLDOWN = 12
        const val KEEPALIVE_FRAMES = 60
        const val PRUNE_BUDGET = 4
        const val GROWTH_TIPS = 64
        const val CROSS_LINK_P = 0.35f
        const val HUE_SEED_LO = 0.70f
        const val HUE_SEED_HI = 0.80f
        const val HUE_LO = 0.66f
        const val HUE_HI = 0.84f
        const val BODY_HUE_STEP = 0.05f
        const val BRANCH_HUE_STEP = 0.06f
        const val SPORE_HUE_STEP = 0.04f
        const val PULSE_HUE = 0.52f
        const val AMBER_HUE = 0.09f
    }

    private class Tip(
        var node: Int,
        var angle: Float,
        var depth: Int,
        var hueOff: Float,
        var energy: Float,
        val bodyIndex: Int,
        var cadence: Int,
        var phase: Int
    )

    private class Pulse(
        var edge: Int,
        var t: Float,
        val speed: Float,
        var energy: Float,
        val generation: Int,
        val hueOff: Float
    )

    private class Spore(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Int,
        val maxLife: Int,
        val hueOff: Float,
        val size: Float
    )

    private class Body(val node: Int, val hueOff: Float, var light: Float)

    private class Ring(var x: Float, var y: Float, var radius: Float, var alpha: Float)

    private val rng = Random(seed)
    private var hue = rng.nextFloat() * (HUE_SEED_HI - HUE_SEED_LO) + HUE_SEED_LO
    private var phase = 0f
    private var frames = 0
    private var lastEmit = -PULSE_COOLDOWN
    private var bassPrev = 0f
    private var beatPrev = 0f
    private var warmup = false

    // Node pool.
    private val nodeX = FloatArray(MAX_NODES)
    private val nodeY = FloatArray(MAX_NODES)
    private val nodeAlive = BooleanArray(MAX_NODES)
    private val nodeEdges = Array(MAX_NODES) { ArrayList<Int>(4) }
    private val freeNodes = ArrayList<Int>()

    // Edge pool.
    private val edgeA = IntArray(MAX_EDGES)
    private val edgeB = IntArray(MAX_EDGES)
    private val edgeAge = FloatArray(MAX_EDGES)
    private val edgeLifetime = FloatArray(MAX_EDGES)
    private val edgeHue = FloatArray(MAX_EDGES)
    private val edgeDepth = IntArray(MAX_EDGES)
    private val edgeBody = IntArray(MAX_EDGES)
    private val edgeLive = BooleanArray(MAX_EDGES)
    private val freeEdges = ArrayList<Int>()

    private val tips = ArrayList<Tip>(MAX_TIPS)
    private val pulses = ArrayList<Pulse>(MAX_PULSES)
    private val spores = ArrayList<Spore>(MAX_SPORES)
    private val bodies = ArrayList<Body>(BODY_COUNT)
    private val rings = ArrayList<Ring>(MAX_RINGS)
    private val sparks = ArrayList<FloatArray>(256)

    /** Number of spores that took root in a new filament. */
    var germinations = 0
        private set

    var width = 0
        private set
    var height = 0
        private set

    val maxEdges = if (lowSpec) MAX_EDGES / 2 else MAX_EDGES
    val maxTips = if (lowSpec) MAX_TIPS / 2 else MAX_TIPS
    val maxPulses = if (lowSpec) MAX_PULSES / 2 else MAX_PULSES
    val maxSpores = if (lowSpec) MAX_SPORES / 2 else MAX_SPORES

    init {
        resize(width, height)
        seedColony()
        emitPulses(0f, 0.5f)
    }

    // ------------------------------------------------------------------
    // pools
    // ------------------------------------------------------------------
    private fun allocNode(x: Float, y: Float): Int {
        if (freeNodes.isNotEmpty()) {
            val index = freeNodes.removeAt(freeNodes.size - 1)
            nodeX[index] = x
            nodeY[index] = y
            nodeAlive[index] = true
            nodeEdges[index].clear()
            return index
        }
        var index = 0
        while (index < MAX_NODES && nodeAlive[index]) index++
        if (index >= MAX_NODES) return -1
        nodeX[index] = x
        nodeY[index] = y
        nodeAlive[index] = true
        nodeEdges[index].clear()
        return index
    }

    private fun freeNode(index: Int) {
        if (index < 0 || index >= MAX_NODES || !nodeAlive[index]) return
        nodeAlive[index] = false
        nodeEdges[index].clear()
        freeNodes.add(index)
    }

    private fun allocEdge(a: Int, b: Int, hueOff: Float, depth: Int, bodyIndex: Int): Int {
        if (freeEdges.isEmpty() && liveEdgeCount() >= maxEdges) {
            if (warmup) return -1
            // No spare filament: trim the leaf nearest the growing tip.
            if (!freeNear(nodeX[b], nodeY[b])) return -1
        }
        val index: Int
        if (freeEdges.isNotEmpty()) {
            index = freeEdges.removeAt(freeEdges.size - 1)
        } else {
            index = firstFreeEdge()
            if (index < 0) return -1
        }
        edgeA[index] = a
        edgeB[index] = b
        edgeAge[index] = 0f
        edgeLifetime[index] = EDGE_LIFETIME * (0.75f + rng.nextFloat() * 0.5f)
        edgeHue[index] = ((hueOff % 1f) + 1f) % 1f
        edgeDepth[index] = depth
        edgeBody[index] = bodyIndex
        edgeLive[index] = true
        nodeEdges[a].add(index)
        nodeEdges[b].add(index)
        return index
    }

    private fun firstFreeEdge(): Int {
        var index = 0
        while (index < MAX_EDGES && edgeLive[index]) index++
        return if (index < MAX_EDGES) index else -1
    }

    private fun liveEdgeCount(): Int {
        var count = 0
        for (i in 0 until MAX_EDGES) if (edgeLive[i]) count++
        return count
    }

    private fun freeEdge(index: Int) {
        if (index < 0 || index >= MAX_EDGES || !edgeLive[index]) return
        val a = edgeA[index]
        val b = edgeB[index]
        nodeEdges[a].remove(index)
        nodeEdges[b].remove(index)
        edgeLive[index] = false
        freeEdges.add(index)
        rehomePulses(index, b)
        if (nodeEdges[a].isEmpty() && !nodeInUse(a)) freeNode(a)
        if (nodeEdges[b].isEmpty() && !nodeInUse(b)) freeNode(b)
    }

    private fun rehomePulses(index: Int, farNode: Int) {
        if (pulses.isEmpty()) return
        val survivors = ArrayList<Pulse>(pulses.size)
        for (pulse in pulses) {
            if (pulse.edge != index) {
                survivors.add(pulse)
                continue
            }
            val candidates = nodeEdges[farNode].filter { it != index && edgeLive[it] }
            if (candidates.isEmpty()) continue
            val chosen = candidates[rng.nextInt(candidates.size)]
            orientEdge(chosen, farNode)
            pulse.edge = chosen
            pulse.t = 0f
            survivors.add(pulse)
        }
        pulses.clear()
        pulses.addAll(survivors)
    }

    /** Trim the leaf filament closest to a growing tip, keeping the web connected. */
    private fun freeNear(x: Float, y: Float): Boolean {
        val protected = tipNodes()
        var best = -1
        var bestLeaf = -1
        var bestDistance = Float.MAX_VALUE
        var bestPriority = 2
        for (index in 0 until MAX_EDGES) {
            if (!edgeLive[index]) continue
            val a = edgeA[index]
            val b = edgeB[index]
            val far = when {
                nodeEdges[b].size <= 1 -> b
                nodeEdges[a].size <= 1 -> a
                else -> continue
            }
            if (!nodeAlive[far]) continue
            val priority = if (protected.contains(far)) 1 else 0
            val dx = nodeX[far] - x
            val dy = nodeY[far] - y
            val distance = dx * dx + dy * dy
            if (best < 0 || priority < bestPriority ||
                (priority == bestPriority && distance < bestDistance)) {
                best = index
                bestLeaf = far
                bestDistance = distance
                bestPriority = priority
            }
        }
        if (best < 0) return false
        if (bestPriority == 1) rerootTip(best, bestLeaf)
        freeEdge(best)
        return true
    }

    private fun tipNodes(): Set<Int> {
        val nodes = HashSet<Int>(tips.size * 2)
        for (tip in tips) nodes.add(tip.node)
        return nodes
    }

    private fun rerootTip(edgeIndex: Int, leaf: Int) {
        val other = if (leaf == edgeA[edgeIndex]) edgeB[edgeIndex] else edgeA[edgeIndex]
        for (tip in tips) if (tip.node == leaf) tip.node = other
    }

    private fun nodeInUse(index: Int): Boolean {
        for (body in bodies) if (body.node == index) return true
        for (tip in tips) if (tip.node == index) return true
        return false
    }

    private fun orientEdge(index: Int, startNode: Int) {
        if (index < 0 || index >= MAX_EDGES || !edgeLive[index]) return
        if (edgeA[index] != startNode && edgeB[index] == startNode) {
            val a = edgeA[index]
            edgeA[index] = edgeB[index]
            edgeB[index] = a
        }
    }

    // ------------------------------------------------------------------
    // construction
    // ------------------------------------------------------------------
    private fun seedColony() {
        if (width <= 0 || height <= 0) return
        placeBodies()
        if (bodies.isEmpty()) return
        seedRootTips()
        warmup = true
        try {
            repeat(WARMUP_STEPS) { grow(1f, 0.35f, 0.45f, 0f, true) }
        } finally {
            warmup = false
        }
        assignCadence()
    }

    private fun placeBodies() {
        val limit = minOf(width, height)
        val baseAngle = rng.nextFloat() * (2.0 * PI).toFloat()
        for (index in 0 until BODY_COUNT) {
            val angle = baseAngle + index * ((2.0 * PI).toFloat() / BODY_COUNT) +
                (rng.nextFloat() - 0.5f) * 0.4f
            val radius = limit * (0.14f + rng.nextFloat() * 0.12f)
            val targetX = width * 0.5f + cos(angle) * radius
            val targetY = height * 0.5f + sin(angle) * radius
            val node = allocNode(targetX, targetY)
            if (node < 0) continue
            bodies.add(Body(node, index * BODY_HUE_STEP, 0f))
        }
    }

    private fun seedRootTips() {
        val centreX = width * 0.5f
        val centreY = height * 0.5f
        for ((index, body) in bodies.withIndex()) {
            if (!nodeAlive[body.node]) continue
            val outward = atan2(nodeY[body.node] - centreY, nodeX[body.node] - centreX)
            for (slot in 0 until ROOT_TIPS_PER_BODY) {
                val offset = (slot - (ROOT_TIPS_PER_BODY - 1) / 2f) * 0.55f
                val angle = outward + offset + (rng.nextFloat() - 0.5f) * 0.24f
                tips.add(Tip(body.node, angle, 0, body.hueOff, 1f, index, 0, 0))
            }
        }
    }

    private fun assignCadence() {
        val cadence = cadence(0f)
        for (tip in tips) {
            tip.cadence = cadence
            tip.phase = rng.nextInt(maxOf(1, cadence))
        }
    }

    // ------------------------------------------------------------------
    // simulation
    // ------------------------------------------------------------------
    private fun cadence(bass: Float): Int {
        val base = maxOf(45, 190 - (bass * 70).toInt())
        val load = maxOf(1f, tips.size.toFloat() / GROWTH_TIPS)
        return (base * load).toInt()
    }

    private fun grow(step: Float, mid: Float, treble: Float, bass: Float, isWarmup: Boolean) {
        val limit = minOf(width, height)
        val cadence = cadence(bass)
        val branchP = 0.10f + mid * 0.18f + treble * 0.10f
        val sporeP = 0.02f + treble * 0.05f
        val nextTips = ArrayList<Tip>(tips.size + 16)
        val nextSparks = ArrayList<FloatArray>(16)
        for (tip in ArrayList(tips)) {
            if (!nodeAlive[tip.node]) continue
            nextTips.add(tip)
            if (tip.depth >= MAX_DEPTH) continue
            if (isWarmup && tip.energy <= 0f) continue
            if (!isWarmup) {
                tip.cadence = cadence
                tip.phase = tip.phase % maxOf(1, cadence)
                if ((frames + tip.phase) % cadence != 0) continue
            }
            val x = nodeX[tip.node]
            val y = nodeY[tip.node]
            var fieldX = sin(y * 0.008f + phase * 1.1f) + 0.6f * cos((x + y) * 0.005f - phase * 0.7f)
            var fieldY = cos(x * 0.009f - phase * 0.9f) + 0.6f * sin((x - y) * 0.005f + phase * 0.6f)
            val magnitude = hypot(fieldX, fieldY)
            if (magnitude > 1e-9f) {
                fieldX /= magnitude; fieldY /= magnitude
            } else {
                fieldX = 1f; fieldY = 0f
            }
            val bodyIndex = tip.bodyIndex
            val bodyX: Float
            val bodyY: Float
            if (bodyIndex in bodies.indices && nodeAlive[bodies[bodyIndex].node]) {
                bodyX = nodeX[bodies[bodyIndex].node]
                bodyY = nodeY[bodies[bodyIndex].node]
            } else {
                bodyX = width * 0.5f
                bodyY = height * 0.5f
            }
            val dx = x - bodyX
            val dy = y - bodyY
            val distance = hypot(dx, dy)
            var swirlX = 0f
            var swirlY = 0f
            if (distance > 1e-6f) {
                val swirl = 0.10f + mid * 0.22f
                swirlX = -dy / distance * swirl
                swirlY = dx / distance * swirl
            }
            var cohX = 0f
            var cohY = 0f
            if (distance > limit && distance > 1e-6f) {
                cohX = -dx / distance * 0.25f
                cohY = -dy / distance * 0.25f
            }
            val headX = cos(tip.angle)
            val headY = sin(tip.angle)
            var dirX = 0.45f * headX + 0.50f * fieldX + swirlX + cohX
            var dirY = 0.45f * headY + 0.50f * fieldY + swirlY + cohY
            val norm = hypot(dirX, dirY)
            if (norm > 1e-9f) {
                dirX /= norm; dirY /= norm
            } else {
                dirX = headX; dirY = headY
            }
            var newAngle = atan2(dirY, dirX)
            val jitter = rng.nextFloat() - 0.5f
            newAngle += (jitter * 2f * (0.05f + mid * 0.10f + treble * 0.05f)).coerceIn(-0.6f, 0.6f)
            val length = maxOf(6f, (16f + bass * 10f) * (1f - min(tip.depth, 8) * 0.03f))
            var newX = x + cos(newAngle) * length
            var newY = y + sin(newAngle) * length
            if (newX < 0f) {
                newX = -newX
                newAngle = PI.toFloat() - newAngle
            } else if (newX > width) {
                newX = 2f * width - newX
                newAngle = PI.toFloat() - newAngle
            }
            if (newY < 0f) {
                newY = -newY
                newAngle = -newAngle
            } else if (newY > height) {
                newY = 2f * height - newY
                newAngle = -newAngle
            }
            newX = newX.coerceIn(0f, width.toFloat())
            newY = newY.coerceIn(0f, height.toFloat())
            val head = allocNode(newX, newY)
            if (head >= 0) {
                val edge = allocEdge(tip.node, head, tip.hueOff, tip.depth, tip.bodyIndex)
                if (edge < 0) {
                    freeNode(head)
                } else {
                    tip.node = head
                    tip.angle = newAngle
                    tip.energy = minOf(1f, tip.energy * 0.96f + 0.04f)
                    nextSparks.add(floatArrayOf(newX, newY))
                    if (rng.nextFloat() < CROSS_LINK_P) {
                        linkNeighbour(head, tip.node, newX, newY, length * 2.8f, tip.hueOff, tip.depth, tip.bodyIndex)
                    }
                    if (rng.nextFloat() < sporeP && spores.size < maxSpores) {
                        val life = rng.nextInt(240, 601)
                        spores.add(Spore(newX, newY, 0f, 0f, life, life,
                            ((tip.hueOff + SPORE_HUE_STEP) % 1f + 1f) % 1f,
                            1.5f + rng.nextFloat() * 2f))
                    }
                    if (rng.nextFloat() < branchP && nextTips.size < maxTips) {
                        val sign = if (rng.nextFloat() < 0.5f) 1f else -1f
                        val offset = 0.4f + rng.nextFloat() * 0.5f
                        val childPhase = rng.nextInt(maxOf(1, cadence))
                        val childEnergy = if (isWarmup) 0f else tip.energy * 0.9f
                        nextTips.add(Tip(head, newAngle + sign * offset, tip.depth + 1,
                            ((tip.hueOff + BRANCH_HUE_STEP) % 1f + 1f) % 1f,
                            childEnergy, tip.bodyIndex, cadence, childPhase))
                    }
                }
            }
        }
        // The tip population is capped: every surviving tip is counted exactly
        // once, so the network can never double its tips within a frame.
        tips.clear()
        tips.addAll(nextTips.take(maxTips))
        sparks.clear()
        sparks.addAll(nextSparks)
        reseedTips()
    }

    private fun linkNeighbour(head: Int, parent: Int, x: Float, y: Float, radius: Float,
                              hueOff: Float, depth: Int, bodyIndex: Int) {
        val limit = radius * radius
        var best = -1
        var bestDistance = limit
        for (index in 0 until MAX_NODES) {
            if (!nodeAlive[index] || index == head || index == parent) continue
            val dx = nodeX[index] - x
            val dy = nodeY[index] - y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        if (best < 0) return
        allocEdge(head, best, hueOff, depth, bodyIndex)
    }

    private fun reseedTips() {
        if (tips.size >= BODY_COUNT || bodies.isEmpty()) return
        for ((index, body) in bodies.withIndex()) {
            if (tips.size >= BODY_COUNT) break
            if (!nodeAlive[body.node]) continue
            val angle = rng.nextFloat() * (2.0 * PI).toFloat()
            val cadence = cadence(0f)
            tips.add(Tip(body.node, angle, 0, body.hueOff, 1f, index, cadence, rng.nextInt(maxOf(1, cadence))))
        }
    }

    private fun prune() {
        var budget = PRUNE_BUDGET
        val protected = tipNodes()
        for (index in 0 until MAX_EDGES) {
            if (!edgeLive[index]) continue
            edgeAge[index] += 1f
            if (edgeAge[index] < edgeLifetime[index] || budget <= 0) continue
            budget--
            val a = edgeA[index]
            val b = edgeB[index]
            if (nodeEdges[b].size <= 1 && !protected.contains(b)) {
                freeEdge(index)
            } else if (nodeEdges[a].size <= 1 && !protected.contains(a)) {
                freeEdge(index)
            } else {
                edgeAge[index] = 0f
                edgeLifetime[index] = EDGE_LIFETIME * (0.75f + rng.nextFloat() * 0.5f)
            }
        }
    }

    private fun nearestBody(x: Float, y: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for ((index, body) in bodies.withIndex()) {
            if (!nodeAlive[body.node]) continue
            val distance = hypot(nodeX[body.node] - x, nodeY[body.node] - y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    private fun emitPulses(mid: Float, bass: Float) {
        val speed = (0.06f + mid * 0.10f) * (0.8f + bass * 0.4f)
        if (pulses.size >= PULSE_TARGET) return
        for (body in bodies) {
            if (pulses.size >= maxPulses) return
            val count = 1 + minOf(2f, bass * 2f).toInt()
            repeat(count) {
                if (pulses.size >= maxPulses) return
                val incident = nodeEdges[body.node].filter { edgeLive[it] }
                var target = -1
                if (incident.isNotEmpty()) {
                    target = incident[rng.nextInt(incident.size)]
                } else {
                    val bodyX = nodeX[body.node]
                    val bodyY = nodeY[body.node]
                    var bestAge = -1f
                    for (index in 0 until MAX_EDGES) {
                        if (!edgeLive[index]) continue
                        val start = edgeA[index]
                        val end = edgeB[index]
                        if (!nodeAlive[start] || !nodeAlive[end]) continue
                        val near = hypot(nodeX[start] - bodyX, nodeY[start] - bodyY) <= 120f ||
                            hypot(nodeX[end] - bodyX, nodeY[end] - bodyY) <= 120f
                        if (near && edgeAge[index] > bestAge) {
                            bestAge = edgeAge[index]
                            target = index
                        }
                    }
                }
                if (target < 0) return@repeat
                if (body.node == edgeA[target] || body.node == edgeB[target]) orientEdge(target, body.node)
                pulses.add(Pulse(target, 0f, speed, 1f, 0, body.hueOff))
            }
        }
    }

    private fun updatePulses(step: Float, mid: Float) {
        val alive = ArrayList<Pulse>(pulses.size + 8)
        val cap = minOf(maxPulses, PULSE_TARGET)
        for (pulse in pulses) {
            if (pulse.edge < 0 || pulse.edge >= MAX_EDGES || !edgeLive[pulse.edge]) continue
            if (pulse.t >= 1f) {
                val destination = edgeB[pulse.edge]
                val candidates = nodeEdges[destination].filter { it != pulse.edge && edgeLive[it] }
                if (candidates.isEmpty()) continue
                val chosen = candidates[rng.nextInt(candidates.size)]
                orientEdge(chosen, destination)
                pulse.edge = chosen
                pulse.t = 0f
                pulse.energy *= 0.92f
                alive.add(pulse)
                if (candidates.size > 1 && rng.nextFloat() < 0.3f + mid * 0.4f && alive.size < cap) {
                    val other = candidates[1 - candidates.indexOf(chosen)]
                    orientEdge(other, destination)
                    alive.add(Pulse(other, 0f, pulse.speed, pulse.energy * 0.75f, pulse.generation + 1, pulse.hueOff))
                }
                continue
            }
            pulse.t = minOf(1f, pulse.t + pulse.speed * step)
            alive.add(pulse)
        }
        pulses.clear()
        pulses.addAll(alive.take(cap))
    }

    private fun updateSpores(step: Float) {
        val survivors = ArrayList<Spore>(spores.size)
        for (spore in spores) {
            var fieldX = sin(spore.y * 0.008f + phase * 1.1f) + 0.6f * cos((spore.x + spore.y) * 0.005f - phase * 0.7f)
            var fieldY = cos(spore.x * 0.009f - phase * 0.9f) + 0.6f * sin((spore.x - spore.y) * 0.005f + phase * 0.6f)
            val magnitude = hypot(fieldX, fieldY)
            if (magnitude > 1e-9f) {
                fieldX /= magnitude; fieldY /= magnitude
            } else {
                fieldX = 1f; fieldY = 0f
            }
            val bodyIndex = nearestBody(spore.x, spore.y)
            var pushX = 0f
            var pushY = 0f
            if (bodies.isNotEmpty() && nodeAlive[bodies[bodyIndex].node]) {
                val bx = nodeX[bodies[bodyIndex].node]
                val by = nodeY[bodies[bodyIndex].node]
                val distance = hypot(spore.x - bx, spore.y - by)
                if (distance > 1e-6f) {
                    pushX = (spore.x - bx) / distance * 0.05f
                    pushY = (spore.y - by) / distance * 0.05f
                }
            }
            spore.vx = (spore.vx + (fieldX + pushX) * 0.12f * step) * 0.96f
            spore.vy = (spore.vy + (fieldY + pushY) * 0.12f * step) * 0.96f
            spore.x = (spore.x + spore.vx * step).coerceIn(0f, width.toFloat())
            spore.y = (spore.y + spore.vy * step).coerceIn(0f, height.toFloat())
            spore.life--
            if (spore.life <= 0) {
                germinate(spore.x, spore.y, spore.hueOff, bodyIndex)
                continue
            }
            survivors.add(spore)
        }
        spores.clear()
        spores.addAll(survivors.take(maxSpores))
    }

    private fun germinate(x: Float, y: Float, hueOff: Float, bodyIndex: Int) {
        val limit = 0.12f * minOf(width, height)
        var nearest = -1
        var nearestDistance = limit
        for (index in 0 until MAX_NODES) {
            if (!nodeAlive[index]) continue
            val distance = hypot(nodeX[index] - x, nodeY[index] - y)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = index
            }
        }
        if (nearest < 0) return
        val node = allocNode(x, y)
        if (node < 0) return
        val sporeHue = ((hueOff + SPORE_HUE_STEP) % 1f + 1f) % 1f
        if (freeEdges.isEmpty() && liveEdgeCount() >= maxEdges) {
            retireOldestEdge(nearest, node)
        }
        val edge = allocEdge(nearest, node, sporeHue, 0, bodyIndex)
        if (edge < 0 || !nodeAlive[nearest]) {
            if (edge >= 0) freeEdge(edge)
            freeNode(node)
            return
        }
        germinations++
        if (tips.size < maxTips) {
            val angle = rng.nextFloat() * (2.0 * PI).toFloat()
            val cadence = cadence(0f)
            tips.add(Tip(node, angle, 0, sporeHue, 1f, bodyIndex, cadence, rng.nextInt(maxOf(1, cadence))))
        }
    }

    private fun retireOldestEdge(protectedA: Int, protectedB: Int) {
        var oldest = -1
        var oldestAge = -1f
        for (index in 0 until MAX_EDGES) {
            if (!edgeLive[index]) continue
            if (edgeA[index] == protectedA || edgeB[index] == protectedA ||
                edgeA[index] == protectedB || edgeB[index] == protectedB) continue
            if (edgeAge[index] > oldestAge) {
                oldestAge = edgeAge[index]
                oldest = index
            }
        }
        if (oldest >= 0) freeEdge(oldest)
    }

    private fun updateRings(step: Float, bass: Float) {
        val alive = ArrayList<Ring>(rings.size)
        for (ring in rings) {
            ring.radius += (1.6f + bass * 3.0f) * step
            ring.alpha -= 0.012f * step
            if (ring.alpha > 0f) alive.add(ring)
        }
        rings.clear()
        rings.addAll(alive)
    }

    private fun spawnRing(x: Float, y: Float) {
        if (rings.size >= MAX_RINGS) rings.removeAt(0)
        rings.add(Ring(x, y, 4f, 1f))
    }

    // ------------------------------------------------------------------
    // frame hook
    // ------------------------------------------------------------------
    /** Advance the colony. [onset] marks a bass onset; [silent] asks for keep-alive light. */
    fun update(step: Float, bass: Float, mid: Float, treble: Float, lightBass: Float,
               onset: Boolean, silent: Boolean) {
        frames++
        val strong = bass >= 0.55f
        val kick = (bass - beatPrev) > 1.0f
        beatPrev = bass
        bassPrev = lightBass
        val silentEmit = silent && frames % 180 == 0

        phase += (0.006f + mid * 0.008f + treble * 0.004f) * step
        var hueValue = hue + (0.0015f + mid * 0.0012f) * step
        if (hueValue > HUE_HI) hueValue = 2f * HUE_HI - hueValue
        else if (hueValue < HUE_LO) hueValue = 2f * HUE_LO - hueValue
        hue = hueValue

        grow(step, mid, treble, bass, false)
        prune()

        val beatDue = onset || kick || strong || silentEmit
        val keepalive = (frames - lastEmit >= KEEPALIVE_FRAMES) && !beatDue
        if ((beatDue || keepalive) && frames - lastEmit >= PULSE_COOLDOWN) {
            emitPulses(mid, bass)
            lastEmit = frames
        }
        if (onset) {
            for (body in bodies) {
                if (!nodeAlive[body.node]) continue
                repeat(rng.nextInt(2, 6)) {
                    if (spores.size >= maxSpores) return@repeat
                    val angle = rng.nextFloat() * (2.0 * PI).toFloat()
                    val speed = 0.5f + rng.nextFloat() * 1.5f
                    val life = rng.nextInt(240, 601)
                    spores.add(Spore(nodeX[body.node], nodeY[body.node],
                        cos(angle) * speed, sin(angle) * speed, life, life,
                        ((body.hueOff + SPORE_HUE_STEP) % 1f + 1f) % 1f,
                        1.5f + rng.nextFloat() * 2f))
                }
                spawnRing(nodeX[body.node], nodeY[body.node])
            }
        }

        updatePulses(step, mid)
        updateSpores(step)
        updateRings(step, bass)
    }

    /** Re-lay out the network for a new viewport; the colony is never rebuilt. */
    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (width > 0 && height > 0 && (newWidth != width || newHeight != height)) {
            val scaleX = newWidth.toFloat() / width
            val scaleY = newHeight.toFloat() / height
            for (index in 0 until MAX_NODES) {
                if (!nodeAlive[index]) continue
                nodeX[index] = (nodeX[index] * scaleX).coerceIn(0f, newWidth.toFloat())
                nodeY[index] = (nodeY[index] * scaleY).coerceIn(0f, newHeight.toFloat())
            }
            for (spore in spores) {
                spore.x = (spore.x * scaleX).coerceIn(0f, newWidth.toFloat())
                spore.y = (spore.y * scaleY).coerceIn(0f, newHeight.toFloat())
                spore.vx *= scaleX
                spore.vy *= scaleY
            }
            for (ring in rings) {
                ring.x = (ring.x * scaleX).coerceIn(0f, newWidth.toFloat())
                ring.y = (ring.y * scaleY).coerceIn(0f, newHeight.toFloat())
                ring.radius *= minOf(scaleX, scaleY)
            }
        }
        width = newWidth
        height = newHeight
    }

    // ------------------------------------------------------------------
    // read-only views for the renderer and the tests
    // ------------------------------------------------------------------
    fun compositeHue(hueOff: Float, depth: Float = 0f): Float {
        val value = hue + hueOff + depth * 0.02f
        val span = HUE_HI - HUE_LO
        if (span <= 0f) return HUE_LO
        return HUE_LO + (value - HUE_LO) % span
    }

    val bodyCount: Int get() = bodies.size
    fun bodyX(index: Int): Float = if (nodeAlive[bodies[index].node]) nodeX[bodies[index].node] else width * 0.5f
    fun bodyY(index: Int): Float = if (nodeAlive[bodies[index].node]) nodeY[bodies[index].node] else height * 0.5f
    fun bodyHue(index: Int): Float = compositeHue(bodies[index].hueOff)
    fun bodyLight(index: Int): Float = bodies[index].light

    fun edgeStart(index: Int): Int = edgeA[index]
    fun edgeEnd(index: Int): Int = edgeB[index]
    fun edgeAgeOf(index: Int): Float = edgeAge[index]
    fun edgeLifetimeOf(index: Int): Float = edgeLifetime[index]
    fun edgeDepthOf(index: Int): Int = edgeDepth[index]
    fun edgeBodyOf(index: Int): Int = edgeBody[index]
    fun edgeHueOf(index: Int): Float = compositeHue(edgeHue[index], edgeDepth[index].toFloat())
    fun edgeAlive(index: Int): Boolean = edgeLive[index]
    fun nodePosX(index: Int): Float = nodeX[index]
    fun nodePosY(index: Int): Float = nodeY[index]

    val pulseCount: Int get() = pulses.size
    fun pulseEdge(index: Int): Int = pulses[index].edge
    fun pulseT(index: Int): Float = pulses[index].t
    fun pulseEnergy(index: Int): Float = pulses[index].energy

    val sporeCount: Int get() = spores.size
    fun sporeX(index: Int): Float = spores[index].x
    fun sporeY(index: Int): Float = spores[index].y
    fun sporeLife(index: Int): Int = spores[index].life
    fun sporeMaxLife(index: Int): Int = spores[index].maxLife
    fun sporeHue(index: Int): Float = spores[index].hueOff
    fun sporeSize(index: Int): Float = spores[index].size

    val ringCount: Int get() = rings.size
    fun ringX(index: Int): Float = rings[index].x
    fun ringY(index: Int): Float = rings[index].y
    fun ringRadius(index: Int): Float = rings[index].radius
    fun ringAlpha(index: Int): Float = rings[index].alpha

    val sparkCount: Int get() = sparks.size
    fun sparkX(index: Int): Float = sparks[index][0]
    fun sparkY(index: Int): Float = sparks[index][1]

    val tipCount: Int get() = tips.size
    val liveEdgeTotal: Int get() = liveEdgeCount()
    val liveNodeTotal: Int get() = (0 until MAX_NODES).count { nodeAlive[it] }
}
