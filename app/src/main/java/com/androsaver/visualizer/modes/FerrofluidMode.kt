package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** Metallic droplet envelope and triangular magnetic peak lattice with springy spikes and a rainbow skin. */
class FerrofluidMode : FullscreenEffectMode() {
    override val name = "Ferrofluid"
    private val rng = kotlin.random.Random(0xF3A0)
    private val poles = Array(5) { floatArrayOf(rng.nextFloat()*1.3f-.65f,rng.nextFloat()*1.3f-.65f) }
    private var time = 0f
    private var hue = 0f
    private var bounce = 0f
    private var bounceVel = 0f
    private var beatPrev = 0f
    override fun reset() { time=0f; hue=0f; bounce=0f; bounceVel=0f; beatPrev=0f }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val r=prepare(draw,audio)
        val bass=r.motion[0]; val mid=r.motion[1]; val high=r.motion[2]
        time+=(.008f+mid*.002f)*r.step*r.motionScale
        hue=(hue+(.001f+high*.0008f)*r.step)%1f
        // Springy spikes: every beat kicks the droplet, it overshoots and rings down.
        if (bass > .65f && beatPrev <= .65f) bounceVel += 1.1f
        beatPrev=bass
        val accel=-bounce*.22f-bounceVel*.30f
        bounceVel+=accel*r.step
        bounce=(bounce+bounceVel*r.step).coerceIn(-.5f,1.5f)
        val a=time*.35f; val c=cos(a); val s=sin(a)
        val uniforms=mutableMapOf("u_time" to floatArrayOf(time),"u_hue" to floatArrayOf(hue),
            "u_bass" to floatArrayOf(bass),"u_high" to floatArrayOf(r.light[2]*r.accent),
            "u_bounce" to floatArrayOf(bounce))
        for (i in poles.indices) { val p=poles[i]; uniforms["u_p$i"]=floatArrayOf(p[0]*c-p[1]*s,p[0]*s+p[1]*c) }
        renderField(draw,uniforms)
    }
    override val fragmentShader = """



uniform vec2 u_resolution, u_p0, u_p1, u_p2, u_p3, u_p4;
uniform float u_time, u_hue, u_bass, u_high, u_bounce;
void main() {
    vec2 p = (gl_FragCoord.xy/u_resolution-0.5)*2.0;
    p.y = -p.y;
    p.x *= u_resolution.x/u_resolution.y;
    vec2 poles[5]; poles[0]=u_p0; poles[1]=u_p1; poles[2]=u_p2; poles[3]=u_p3; poles[4]=u_p4;
    float height=0.0;
    vec2 gradient=vec2(0.0);
    for (int i=0;i<5;++i) {
        vec2 d=p-poles[i];
        float width=0.12+u_bass*0.018;
        float peak=exp(-dot(d,d)/width);
        height+=peak;
        gradient+=-2.0*d/width*peak;
    }
    // Connected liquid envelope supporting a triangular lattice of sharp peaks.
    float envelope=1.0-exp(-height*1.4);
    vec2 envelopeGradient=1.4*exp(-height*1.4)*gradient;
    float ca=cos(u_time*0.12), sa=sin(u_time*0.12);
    vec2 q=mat2(ca,sa,-sa,ca)*p;
    float k=20.0;
    float a=k*q.x, b=k*(0.5*q.x+0.8660254*q.y), c=k*(0.5*q.x-0.8660254*q.y);
    float lattice=(cos(a)+cos(b)+cos(c))/3.0;
    vec2 dg=vec2(-k*(sin(a)+0.5*sin(b)+0.5*sin(c))/3.0,
                 -k*0.8660254*(sin(b)-sin(c))/3.0);
    dg=mat2(ca,-sa,sa,ca)*dg;
    float peak=exp((lattice-1.0)*8.0);
    float amplitude=0.18+u_bass*0.04+u_bounce*0.09;
    gradient=gradient*0.15+amplitude*(envelopeGradient*peak+envelope*peak*8.0*dg);
    float mask=smoothstep(0.06,0.4,height);
    height=height*0.15+amplitude*envelope*peak;
    vec3 normal=normalize(vec3(-gradient,1.0));
    vec3 light=normalize(vec3(-0.5,-0.7,1.0));
    float diffuse=max(dot(normal,light),0.0);
    float spec=pow(max(dot(reflect(-light,normal),vec3(0,0,1)),0.0),40.0);
    float rim=pow(1.0-normal.z,3.0);
    // Rainbow skin: hue follows the spike height and the radius of the droplet.
    float rainbow=fract(u_hue+height*0.55+length(p)*0.35+u_high*0.05);
    vec3 tint=0.5+0.5*cos(6.2831853*(rainbow+vec3(0,0.3333,0.6667)));
    vec3 color=vec3(0.006,0.008,0.014)+mask*(tint*(0.05+diffuse*0.30+rim*0.55)+vec3(spec)*(0.8+u_high*0.08));
    gl_FragColor=vec4(clamp(color,0.0,1.0),1.0);
}
    """.trimIndent()
}
