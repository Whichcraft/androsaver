package com.androsaver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamInteractionPolicyTest {
    @Test fun slideshowReceivesRemoteInput() {
        assertTrue(DreamInteractionPolicy.isInteractive(Prefs.MODE_SLIDESHOW))
    }

    @Test fun visualizerAndBlankRemainInteractive() {
        assertTrue(DreamInteractionPolicy.isInteractive(Prefs.MODE_VISUALIZER))
        assertTrue(DreamInteractionPolicy.isInteractive(Prefs.MODE_BLANK))
    }

    @Test fun staticImageRemainsNonInteractive() {
        assertFalse(DreamInteractionPolicy.isInteractive(Prefs.MODE_STATIC))
    }
}
