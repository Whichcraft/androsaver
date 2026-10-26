package com.androsaver.visualizer

import kotlin.math.pow

/** psysuals 3.20 timing and separated geometry/light responses, independent of render FPS. */
class EffectDynamics {
    var step = 1f; private set
    val motion = FloatArray(3)
    val light = FloatArray(3)
    var motionScale = .65f; private set
    var accent = 1f; private set
    var profile = "laptop"; private set
    fun update(audio: AudioData, width: Int) {
        step = if (audio.deltaSeconds.isFinite()) audio.deltaSeconds.coerceIn(0f, .1f) * 60f else 1f
        profile = when (audio.visualProfile) { "tv" -> "tv"; "laptop" -> "laptop"; else -> if (width >= 2560) "tv" else "laptop" }
        motionScale = if (profile == "tv") .85f else .65f
        accent = if (profile == "tv") 1.3f else 1f
        for (i in 0..2) {
            val raw = when (i) { 0 -> audio.beat; 1 -> audio.mid; else -> audio.treble }
            val target = if (raw.isFinite()) raw.coerceIn(0f, 1.5f) else 0f
            motion[i] = smooth(motion[i], target, .035f)
            light[i] = smooth(light[i], target, .25f)
        }
    }
    fun smooth(current: Float, target: Float, rate: Float): Float = current + (target-current) * (1f-(1f-rate).pow(step))
    fun fade(alpha: Float): Float = 1f-(1f-alpha.coerceIn(0f,1f)).pow(step)
    fun reset() { motion.fill(0f); light.fill(0f); step = 1f }
}
