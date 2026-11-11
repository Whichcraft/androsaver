package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw

/** Eightfold iridescent weave; same field equations as upstream ChromaWeave. */
class ChromaWeaveMode : FullscreenEffectMode() {
    override val name = "ChromaWeave"
    private var time = 0f
    private var hue = .54f
    private var turn = 0f
    private val uniforms = mapOf(
        "u_time" to FloatArray(1), "u_hue" to FloatArray(1),
        "u_turn" to FloatArray(1), "u_bass" to FloatArray(1),
        "u_mid" to FloatArray(1), "u_high" to FloatArray(1),
        "u_pulse" to FloatArray(1), "u_accent" to FloatArray(1)
    )

    override fun reset() {
        time = 0f; hue = .54f; turn = 0f
        resetDynamics()
    }

    private fun advance(draw: GLDraw, audio: AudioData) {
        val response = prepare(draw, audio)
        val dt = response.step / 60f
        time += dt * (1f + response.motion[1] * .18f)
        turn += dt * .09f * response.motionScale
        hue = (hue + dt * .018f) % 1f
        uniforms.getValue("u_time")[0] = time
        uniforms.getValue("u_hue")[0] = hue
        uniforms.getValue("u_turn")[0] = turn
        uniforms.getValue("u_bass")[0] = response.motion[0]
        uniforms.getValue("u_mid")[0] = response.motion[1]
        uniforms.getValue("u_high")[0] = response.light[2]
        uniforms.getValue("u_pulse")[0] = response.light[0]
        uniforms.getValue("u_accent")[0] = response.accent
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        advance(draw, audio)
        renderField(draw, uniforms)
    }

    override val fragmentShader = """
uniform vec2 u_resolution;
uniform float u_time, u_hue, u_turn, u_bass, u_mid, u_high, u_pulse, u_accent;

vec3 weaveColour(float hue) {
    return 0.5 + 0.5*cos(6.2831853*(hue + vec3(0.0,0.3333,0.6667)));
}
void main() {
    vec2 p = vec2(2.0*gl_FragCoord.x-u_resolution.x,
                  2.0*gl_FragCoord.y-u_resolution.y)/u_resolution.y;
    float ca=cos(u_turn), sa=sin(u_turn);
    p=mat2(ca,sa,-sa,ca)*p;
    p/=1.0+u_bass*0.06+u_pulse*0.10;
    float radius=length(p);
    float angle=atan(p.y,p.x+0.000001);
    float wedge=0.7853981634;
    float folded=abs(mod(angle+wedge*0.5,wedge)-wedge*0.5);
    vec2 q=radius*vec2(cos(folded),sin(folded));
    float centre=smoothstep(0.07,0.25,radius);
    vec3 colour=vec3(0.006,0.004,0.025);
    for(int i=0;i<3;++i) {
        float layer=float(i);
        vec2 z=q*(1.0+layer*0.38);
        z+=vec2(0.15*sin(u_time*0.19+layer),0.12*cos(u_time*0.13+layer));
        z.x+=0.16*sin(z.y*5.0+u_time*0.23+layer);
        z.y+=0.12*sin(z.x*4.0-u_time*0.19);
        float weave=sin(z.x*6.0-u_time*(0.75+u_mid*0.12)+sin(z.y*8.0+u_time*0.31))
                    *cos(z.y*10.0+u_time*0.4);
        float thread=exp(-90.0*weave*weave);
        float halo=exp(-8.0*abs(weave));
        float over=0.5+0.5*sin(z.x*7.0+z.y*5.0-u_time*0.42);
        float distance=radius-(0.42+layer*0.46+0.12*cos(angle*8.0+u_time*0.25+layer));
        float petal=exp(-65.0*distance*distance);
        float hue=u_hue+layer*0.19+radius*0.15+folded*0.45+weave*0.08;
        float light=0.32*petal+(0.65+u_pulse*0.25*u_accent)*thread*(0.42+over*0.58)+halo*0.16;
        colour+=weaveColour(hue)*light*centre/(1.0+layer*0.6);
        float sparkle=pow(max(0.0,sin(z.x*12.0+u_time*0.5)*cos(z.y*13.0-u_time*0.2)),18.0);
        colour+=vec3(0.25,0.8,1.0)*sparkle*thread*u_high*0.16*u_accent*centre;
    }
    colour/=1.0+radius*radius*0.3;
    gl_FragColor=vec4(1.0-exp(-colour*2.0),1.0);
}
    """.trimIndent()
}
