package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.floor

/**
 * Light on the floor of a pool: a shifting net of bright lines cast through moving water.
 *
 * Port of upstream psysuals Caustics. Upstream evaluates a 256-wide CPU grid (numpy) and scales it
 * up; here the same maths runs per pixel in a GLES2 fragment shader. The water surface is a sum of
 * five travelling waves plus ripples; brightness is the inverse Jacobian of the refracted light map,
 * 1 / (|(1+f*hxx)(1+f*hyy) - (f*hxy)^2| + 0.22).
 *
 * Android adaptations:
 *  - The surface Hessian is evaluated analytically in the shader (no finite-difference grid, no
 *    [1,2,1] blur, no managed buffers), so the lines are crisp at any resolution.
 *  - Bass/mid/treble come from [com.androsaver.visualizer.EffectDynamics] (smoothed beat, mid and
 *    treble deviations, clamped 0..1.5); mid is clamped to 0..1 for speed so peaks cannot make the
 *    waves jump. Beat ripples use the raw [AudioData.beat] rising edge across 0.6, as upstream.
 *  - All motion is phase accumulated from elapsed time per frame; audio only scales the rate.
 *  - Treble glints are a per-pixel hash re-seeded 20 times a second instead of a numpy RNG mask.
 *  - Ripple state is passed as fixed uniforms (x, y, age) and strengths; unused slots have strength 0.
 *  - World y is flipped so the sand is brightest at the bottom of the screen like upstream's image rows.
 */
class CausticsMode : FullscreenEffectMode() {
    override val name = "Caustics"

    private var time = 0f
    private var hue = .5f
    private var wind = 0f
    private var beatPrev = 0f
    private var rng = kotlin.random.Random(SEED)

    /** Per-wave phase in radians, wrapped to 0..2pi. */
    internal val phase = FloatArray(WAVE_COUNT)
    internal var rippleCount = 0; private set
    internal val rippleX = FloatArray(MAX_RIPPLES)
    internal val rippleY = FloatArray(MAX_RIPPLES)
    internal val rippleAge = FloatArray(MAX_RIPPLES)
    internal val rippleStrength = FloatArray(MAX_RIPPLES)

    private val phaseA = FloatArray(3)
    private val phaseB = FloatArray(2)
    private val params1 = FloatArray(3)   // wind, hue, bass
    private val params2 = FloatArray(3)   // treble, flash, glint seed
    private val strengthA = FloatArray(3)
    private val strengthB = FloatArray(3)
    private val ripples = Array(MAX_RIPPLES) { FloatArray(3) }
    internal val uniforms: Map<String, FloatArray> = LinkedHashMap<String, FloatArray>().apply {
        put("u_phaseA", phaseA); put("u_phaseB", phaseB)
        put("u_p1", params1); put("u_p2", params2)
        put("u_sA", strengthA); put("u_sB", strengthB)
        for (i in 0 until MAX_RIPPLES) put("u_r$i", ripples[i])
    }

    override fun reset() {
        time = 0f; hue = .5f; wind = 0f; beatPrev = 0f
        rng = kotlin.random.Random(SEED)
        phase.fill(0f); rippleCount = 0
        rippleX.fill(0f); rippleY.fill(0f); rippleAge.fill(0f); rippleStrength.fill(0f)
        resetDynamics()
        publish(0f, 0f, 0f, 0f)
    }

    internal fun advance(draw: GLDraw, audio: AudioData) {
        val response = prepare(draw, audio)
        val dt = response.step / 60f
        val bass = response.motion[0].coerceIn(0f, 1.5f)
        val mid = response.motion[1].coerceIn(0f, 1f)
        val high = response.light[2].coerceIn(0f, 1.5f).let { if (response.motion[2] > it) response.motion[2] else it }
        val flash = response.light[0] * response.accent
        val scale = response.motionScale

        time += dt
        hue = (hue + (.004f + mid * .004f) * dt) % 1f
        wind = (wind + (.02f + mid * .05f) * dt * scale) % TAU
        val rate = (.18f + .30f * mid) * dt * scale
        for (i in 0 until WAVE_COUNT) {
            phase[i] = (phase[i] + TAU / WAVE_LENGTH[i] * WAVE_SPEED[i] * rate) % TAU
        }

        val beat = if (audio.beat.isFinite()) audio.beat else 0f
        if (beat > .6f && beatPrev <= .6f && rippleCount < MAX_RIPPLES) {
            val aspect = draw.W.toFloat() / maxOf(1, draw.H)
            val n = rippleCount++
            rippleX[n] = .15f + rng.nextFloat() * (2f * aspect - .3f)
            rippleY[n] = .15f + rng.nextFloat() * 1.7f
            rippleAge[n] = 0f
            rippleStrength[n] = minOf(1.5f, beat)
        }
        beatPrev = beat
        var kept = 0
        for (i in 0 until rippleCount) {
            rippleAge[i] += dt
            if (rippleAge[i] < RIPPLE_LIFE) {
                if (kept != i) {
                    rippleX[kept] = rippleX[i]; rippleY[kept] = rippleY[i]
                    rippleAge[kept] = rippleAge[i]; rippleStrength[kept] = rippleStrength[i]
                }
                kept++
            }
        }
        rippleCount = kept
        publish(bass, high, flash, mid)
    }

    private fun publish(bass: Float, high: Float, flash: Float, @Suppress("UNUSED_PARAMETER") mid: Float) {
        phaseA[0] = phase[0]; phaseA[1] = phase[1]; phaseA[2] = phase[2]
        phaseB[0] = phase[3]; phaseB[1] = phase[4]
        params1[0] = wind; params1[1] = hue; params1[2] = bass
        params2[0] = high; params2[1] = flash
        params2[2] = floor(time * 20f) % 251f
        for (i in 0 until MAX_RIPPLES) {
            val live = i < rippleCount
            ripples[i][0] = if (live) rippleX[i] else 0f
            ripples[i][1] = if (live) rippleY[i] else 0f
            ripples[i][2] = if (live) rippleAge[i] else 0f
            val s = if (live) rippleStrength[i] else 0f
            if (i < 3) strengthA[i] = s else strengthB[i - 3] = s
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        advance(draw, audio)
        renderField(draw, uniforms)
    }

    companion object {
        internal const val MAX_RIPPLES = 6
        internal const val WAVE_COUNT = 5
        internal const val RIPPLE_LIFE = 5f
        private const val SEED = 7
        private const val TAU = 6.2831855f
        private val WAVE_LENGTH = floatArrayOf(.62f, .41f, .29f, .53f, .22f)
        private val WAVE_SPEED = floatArrayOf(.55f, .62f, .78f, .50f, .90f)
    }

    override val fragmentShader = """
uniform vec2 u_resolution;
uniform vec3 u_phaseA, u_p1, u_p2, u_sA, u_sB;
uniform vec2 u_phaseB;
uniform vec3 u_r0, u_r1, u_r2, u_r3, u_r4, u_r5;

vec3 hsl(float h, float s, float l) {
    float c = (1.0 - abs(2.0*l - 1.0)) * s;
    vec3 k = clamp(abs(mod(fract(h)*6.0 + vec3(0.0,4.0,2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return l + c*(k - 0.5);
}
float hash(vec2 c) {
    return fract(sin(dot(c, vec2(12.9898, 78.233))) * 43758.5453);
}
// Hessian (hxx, hxy, hyy) contribution of one travelling wave.
void addWave(vec2 p, float len, float amp, float off, float ph, float wind, float scale, inout vec3 hess) {
    float k = 6.2831853 / len;
    float ang = wind + off;
    float c = cos(ang), s = sin(ang);
    float w = -amp * scale * k * k * sin(k*(p.x*c + p.y*s) - ph);
    hess += w * vec3(c*c, c*s, s*s);
}
// Hessian contribution of one expanding ripple (x, y, age) with strength st.
void addRipple(vec2 p, vec3 r, float st, inout vec3 hess) {
    if (st <= 0.0) return;
    vec2 d = p - r.xy;
    float rad = max(length(d), 0.001);
    vec2 n = d / rad;
    float f = rad - 0.55*r.z;
    float a = 20.661157;
    float kk = 14.0;
    float e = 0.010 * st * exp(-r.z/1.4) * exp(-a*f*f);
    float cs = cos(kk*f), sn = sin(kk*f);
    float g1 = e*(-2.0*a*f*cs - kk*sn);
    float g2 = e*((4.0*a*a*f*f - 2.0*a - kk*kk)*cs + 4.0*a*f*kk*sn);
    float q = g1 / rad;
    hess += vec3(g2*n.x*n.x + q*(1.0 - n.x*n.x),
                 (g2 - q)*n.x*n.y,
                 g2*n.y*n.y + q*(1.0 - n.y*n.y));
}
void main() {
    float wind = u_p1.x, hue = u_p1.y, bass = u_p1.z;
    float high = u_p2.x, flash = u_p2.y;
    vec2 p = gl_FragCoord.xy / u_resolution.y * 2.0;
    float scale = 0.0075 * (1.0 + 1.3*bass);
    vec3 hess = vec3(0.0);
    addWave(p, 0.62, 1.00, 0.00, u_phaseA.x, wind, scale, hess);
    addWave(p, 0.41, 0.70, 1.10, u_phaseA.y, wind, scale, hess);
    addWave(p, 0.29, 0.45, 2.30, u_phaseA.z, wind, scale, hess);
    addWave(p, 0.53, 0.60, 3.70, u_phaseB.x, wind, scale, hess);
    addWave(p, 0.22, 0.30, 4.90, u_phaseB.y, wind, scale, hess);
    addRipple(p, u_r0, u_sA.x, hess);
    addRipple(p, u_r1, u_sA.y, hess);
    addRipple(p, u_r2, u_sA.z, hess);
    addRipple(p, u_r3, u_sB.x, hess);
    addRipple(p, u_r4, u_sB.y, hess);
    addRipple(p, u_r5, u_sB.z, hess);

    float focus = 1.0 + 0.4*bass;
    float jac = (1.0 + focus*hess.x)*(1.0 + focus*hess.z) - focus*focus*hess.y*hess.y;
    float level = clamp(1.0/(abs(jac) + 0.22), 0.0, 4.5);
    float net = pow(max(clamp((level - 1.0)/2.2, 0.0, 1.0), 0.0001), 0.8);

    // Static sand: soft blotches plus a fine grain.
    float blotch = sin(p.x*3.1 + 1.7)*cos(p.y*4.4 + 0.6)
                 + sin(p.x*5.2 + 4.1)*cos(p.y*2.3 + 2.9)
                 + sin(p.x*1.9 + 3.3)*cos(p.y*5.7 + 5.2)
                 + sin(p.x*4.6 + 0.4)*cos(p.y*3.5 + 1.1);
    vec2 cell = floor(gl_FragCoord.xy / max(1.0, u_resolution.y/144.0));
    float sand = 0.78 + 0.10*blotch/4.0 + 0.10*(hash(cell) - 0.5);
    float depth = 1.0 - 0.45*p.y/2.0;
    vec3 floorCol = hsl(hue + 0.04, 0.75, 0.40);
    vec3 sandCol = hsl(0.11, 0.40, 0.55);
    vec3 ray = hsl(hue - 0.03, 0.80, 0.78);
    vec3 base = sand*depth*(0.65*floorCol + 0.35*sandCol);
    float glow = 0.70 + 0.40*bass + 0.70*flash;
    vec3 rgb = base*(0.70 + 0.30*net) + net*ray*glow;
    if (high > 0.05) {
        float rnd = hash(cell + vec2(u_p2.z*1.37, u_p2.z*2.71));
        if (level > 2.8 && rnd < 0.30*high) rgb = vec3(1.0);
        rgb += net*hsl(hue + 0.12, 0.9, 0.5)*0.9*high;
    }
    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
    """.trimIndent()
}
