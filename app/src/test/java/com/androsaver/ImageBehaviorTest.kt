package com.androsaver

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageBehaviorTest {
    @Test fun staticDefaultsAreCropLandscapeFitPortrait() {
        assertEquals(ImageBehavior.CROP, ImageBehavior.defaultFor(Prefs.MODE_STATIC, false))
        assertEquals(ImageBehavior.FIT, ImageBehavior.defaultFor(Prefs.MODE_STATIC, true))
    }

    @Test fun slideshowDefaultsFitBothOrientations() {
        assertEquals(ImageBehavior.FIT, ImageBehavior.defaultFor(Prefs.MODE_SLIDESHOW, false))
        assertEquals(ImageBehavior.FIT, ImageBehavior.defaultFor(Prefs.MODE_SLIDESHOW, true))
    }

    @Test fun invalidValuesUseModeDefault() {
        assertEquals(ImageBehavior.CROP, ImageBehavior.resolve(Prefs.MODE_STATIC, false, "bogus", null))
        assertEquals(ImageBehavior.FIT, ImageBehavior.resolve(Prefs.MODE_SLIDESHOW, true, null, "bogus"))
    }
}
