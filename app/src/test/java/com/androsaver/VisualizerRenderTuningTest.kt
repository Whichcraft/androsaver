package com.androsaver

import com.androsaver.visualizer.VisualizerRenderTuning
import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.CymaticaMode
import com.androsaver.visualizer.modes.FerrofluidMode
import com.androsaver.visualizer.modes.HyperbolicMode
import com.androsaver.visualizer.modes.LiquidLightMode
import com.androsaver.visualizer.modes.MandelboxMode
import com.androsaver.visualizer.modes.MorphogenesisMode
import com.androsaver.visualizer.modes.PhasonMode
import com.androsaver.visualizer.modes.TesseractMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerRenderTuningTest {
    @Test fun fieldGridScalesWithinSafeBounds() {
        assertEquals(24, VisualizerRenderTuning.fieldColumns(640, 360))
        assertEquals(32, VisualizerRenderTuning.fieldColumns(1920, 1080))
        assertEquals(40, VisualizerRenderTuning.fieldColumns(3840, 2160))
        assertTrue(VisualizerRenderTuning.fieldRows(3840, 2160) in 12..28)
        assertTrue(VisualizerRenderTuning.fieldRows(1080, 1920) in 12..28)
    }

    @Test fun viewportScaleIsBoundedForAllTargetOrientations() {
        assertEquals(0.5f, VisualizerRenderTuning.viewportScale(320, 240))
        assertEquals(1f, VisualizerRenderTuning.viewportScale(1920, 1080))
        assertEquals(2f, VisualizerRenderTuning.viewportScale(3840, 2160))
        assertEquals(1f, VisualizerRenderTuning.viewportScale(1080, 1920))
    }

    @Test fun splitPsysualsModesRenderAtTargetViewports() {
        val sizes = listOf(1920 to 1080, 3840 to 2160, 1080 to 1920, 320 to 240)
        sizes.forEach { (width, height) ->
            listOf(
                AudioData(fft = FloatArray(4), beat = 0.4f, mid = 0.2f, treble = 0.1f),
                AudioData(fft = FloatArray(0))
            ).forEach { audio ->
                listOf(
                    MorphogenesisMode(), PhasonMode(), CymaticaMode(), LiquidLightMode(),
                    MandelboxMode(), HyperbolicMode(), TesseractMode(), FerrofluidMode()
                ).forEach { mode ->
                    val draw = NoOpDraw(width, height)
                    mode.onSurfaceCreated()
                    mode.draw(draw, audio, 1)
                    mode.reset()
                    assertTrue(mode.name.isNotBlank())
                }
            }
        }
    }

    private class NoOpDraw(width: Int, height: Int) : GLDraw(width, height) {
        override fun setAdditiveBlend() = Unit
        override fun setNormalBlend() = Unit
        override fun fadeBlack(alpha: Float) = Unit
        override fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) = Unit
        override fun circle(cx: Float, cy: Float, radius: Float, r: Float, g: Float, b: Float, a: Float, filled: Boolean, segments: Int) = Unit
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) = Unit
        override fun lineStrip(pts: FloatArray, pointCount: Int, r: Float, g: Float, b: Float, a: Float) = Unit
    }
}
