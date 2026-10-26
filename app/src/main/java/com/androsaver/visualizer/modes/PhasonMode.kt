package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw

/** Stable seven-wave field and slow bounded pulse, matching upstream 3.20. */
class PhasonMode : FullscreenEffectMode() {
    override val name="Phason"
    private var time=0f
    private var hue=0f
    private var pulse=0f
    override fun reset() { time=0f; hue=0f; pulse=0f }
    override fun draw(draw: GLDraw,audio: AudioData,tick: Int) {
        val r=prepare(draw,audio); val bass=r.motion[0]; val mid=r.motion[1]; val high=r.motion[2]
        time+=(.004f+mid*.001f)*r.step
        hue=(hue+(.00025f+mid*.0002f)*r.step)%1f
        pulse=r.smooth(pulse,minOf(.12f,bass*.08f),.025f)
        renderField(draw,mapOf("u_time" to floatArrayOf(time),"u_hue" to floatArrayOf(hue),
            "u_bass" to floatArrayOf(bass),"u_mid" to floatArrayOf(mid),"u_high" to floatArrayOf(high),"u_pulse" to floatArrayOf(pulse)))
    }
    override val fragmentShader="""



uniform vec2 u_resolution;
uniform float u_time, u_hue, u_bass, u_mid, u_high, u_pulse;

vec3 palette(float h, float v) {
    return clamp(0.5 + 0.5*cos(6.2831853*(h + vec3(0.0,0.3333,0.6667))),0.0,1.0)*v;
}
void main() {
    vec2 p = (gl_FragCoord.xy / u_resolution - 0.5) * vec2(12.56637,12.56637);
    float v = 0.0;
    for (int i=0; i<7; ++i) {

        float a = 6.2831853*float(i)/7.0 + sin(u_time*0.13+float(i))*0.16;
        vec2 k = vec2(cos(a),sin(a));
        v += cos(dot(p,k)*(0.82+u_mid*0.035) + u_time*(0.30+float(i)*0.047));
    }
    v /= 7.0;
    v += cos((p.x+p.y)*0.5-u_time*0.7)*(0.20+u_pulse*0.25);
    float bands = 0.5+0.5*cos(v*11.0 + u_time*0.42);
    float hue = u_hue + v*0.21 + bands*0.22 + u_high*0.025;
    float light = clamp(0.12 + bands*0.62 + u_bass*0.10 + u_pulse*0.13,0.0,0.95);
    gl_FragColor = vec4(palette(hue, light),1.0);
}
    """.trimIndent()
}
