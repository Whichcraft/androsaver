package com.androsaver

/** Whether a DreamService mode must receive remote/input events. */
object DreamInteractionPolicy {
    fun isInteractive(mode: String?): Boolean = mode == Prefs.MODE_VISUALIZER ||
        mode == Prefs.MODE_SLIDESHOW ||
        mode == Prefs.MODE_BLANK
}
