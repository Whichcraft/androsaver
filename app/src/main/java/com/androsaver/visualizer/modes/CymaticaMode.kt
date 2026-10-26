package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class CymaticaMode : PsysualsFieldMode() {
    override val name = "Cymatica"
    private val modes = arrayOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 2 to 5, 3 to 6)
    private val weights=FloatArray(6).apply { this[0]=1f }
    private val modeA = FloatArray(cols * rows)
    private val modeB = FloatArray(cols * rows)
    private val particleX = FloatArray(700) { kotlin.random.Random(0xC7A + it).nextFloat() * 2f - 1f }
    private val particleY = FloatArray(700) { kotlin.random.Random(0xD11 + it).nextFloat() * 2f - 1f }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val response=prepare(draw,audio);val step=response.step
        val bass=response.motion[0];val mid=response.motion[1];val high=response.motion[2]
        hue=(hue+(.0004f+high*.0002f)*step)%1f
        val energies=FloatArray(6)
        val count=minOf(128,audio.fft.size)
        for(i in energies.indices) {
            val from=i*count/6;val to=(i+1)*count/6
            var total=0f;for(j in from until to) total+=abs(audio.fft[j])
            energies[i]=.05f+if(to>from) total/(to-from) else 0f
        }
        val sum=energies.sum()
        for(i in weights.indices) weights[i]=response.smooth(weights[i],energies[i]/sum,.003f)
        modeB.fill(0f)
        for(i in modes.indices) {
            fillMode(modeA,modes[i].first,modes[i].second)
            for(j in modeB.indices) modeB[j]+=weights[i]*modeA[j]
        }
        for(i in values.indices) values[i]=exp(-abs(modeB[i])*(12f-high*1.5f)).coerceIn(0f,1f)
        paint(draw, audio)

        draw.setAdditiveBlend()
        val radius = psysualsViewportScale(draw)
        for (i in particleX.indices step 2) {
            val px=particleX[i];val py=particleY[i]
            var field=0f;var gx=0f;var gy=0f
            for(j in modes.indices) {
                val m=modes[j].first*PI.toFloat();val n=modes[j].second*PI.toFloat()
                field+=weights[j]*cos(m*px)*cos(n*py)
                gx-=weights[j]*m*sin(m*px)*cos(n*py)
                gy-=weights[j]*n*cos(m*px)*sin(n*py)
            }
            particleX[i]=(px-(field*gx*.0008f*step).coerceIn(-.002f*step,.002f*step)).coerceIn(-.995f,.995f)
            particleY[i]=(py-(field*gy*.0008f*step).coerceIn(-.002f*step,.002f*step)).coerceIn(-.995f,.995f)
            val x = (particleX[i] + 1f) * draw.W * 0.5f
            val y = (particleY[i] + 1f) * draw.H * 0.5f
            draw.circle(x, y, radius, 0.86f, 0.96f, 1f, 0.88f, true, 6)
        }
        draw.setNormalBlend()
    }

    override fun reset() {
        super.reset()
        modeA.fill(0f); modeB.fill(0f);weights.fill(0f);weights[0]=1f
    }

    private fun fillMode(out: FloatArray, m: Int, n: Int) {
        for (y in 0 until rows) for (x in 0 until cols) {
            val px = x / (cols - 1f) * 2f - 1f
            val py = y / (rows - 1f) * 2f - 1f
            out[y * cols + x] = cos(PI.toFloat() * m * px) * cos(PI.toFloat() * n * py)
        }
    }

    private fun wrap(value: Float): Float = ((value + 1f) % 2f + 2f) % 2f - 1f
}
