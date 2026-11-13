package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import com.androsaver.visualizer.modes.CubeMode
import com.androsaver.visualizer.modes.SpiralMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trail-fade contracts for the effects whose glow was retuned for 2.13.0:
 * Cube keeps the slow psysuals 18/255 fade, Spiral fades faster than the
 * shared floor so its arms stay crisp.
 */
class VisualizerTrailFadeTest {

    @Test fun cubeUsesTheSlowPsysualsTrailFade() {
        val draw = FadeCaptureDraw()
        val mode = CubeMode()
        mode.onSurfaceCreated()
        mode.draw(draw, AudioData(deltaSeconds = 1f / 60f), 0)

        // Below the shared floor: the cube opts into a slower fade explicitly.
        assertTrue(draw.fadeAlpha < VisualizerRenderTuning.MIN_TRAIL_FADE)
        assertEquals(18f / 255f, draw.fadeAlpha, 1e-4f)
    }

    @Test fun cubeTrailFadeIsFrameRateCorrected() {
        val atSixty = FadeCaptureDraw()
        CubeMode().draw(atSixty, AudioData(deltaSeconds = 1f / 60f), 0)
        val atThirty = FadeCaptureDraw()
        CubeMode().draw(atThirty, AudioData(deltaSeconds = 1f / 30f), 0)

        // Two half-length frames must retain exactly as much as one full frame.
        assertEquals((1f - atSixty.fadeAlpha) * (1f - atSixty.fadeAlpha), 1f - atThirty.fadeAlpha, 1e-4f)
    }

    @Test fun spiralFadesFasterThanTheSharedFloor() {
        val draw = FadeCaptureDraw()
        val mode = SpiralMode()
        mode.onSurfaceCreated()
        mode.draw(draw, AudioData(deltaSeconds = 1f / 60f), 0)

        assertTrue(draw.fadeAlpha > VisualizerRenderTuning.MIN_TRAIL_FADE)
        assertEquals(0.28f, draw.fadeAlpha, 1e-4f)
    }

    /** Records the alpha of the first full-screen black quad, i.e. the trail fade. */
    private class FadeCaptureDraw : GLDraw(640, 360) {
        var fadeAlpha = -1f

        override fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
            if (fadeAlpha < 0f && r == 0f && g == 0f && b == 0f && w >= W && h >= H) fadeAlpha = a
        }

        override fun setAdditiveBlend() = Unit
        override fun setNormalBlend() = Unit
        override fun circle(cx: Float, cy: Float, radius: Float, r: Float, g: Float, b: Float, a: Float, filled: Boolean, segments: Int) = Unit
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) = Unit
    }
}
