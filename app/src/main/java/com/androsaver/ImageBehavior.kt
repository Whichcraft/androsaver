package com.androsaver

/** Pure image-behavior defaults and validation shared by static/slideshow paths. */
object ImageBehavior {
    const val CROP = "crop"
    const val FIT = "fit"
    const val CENTER = "center"
    const val STRETCH = "stretch"
    private val allowed = setOf(CROP, FIT, CENTER, STRETCH)

    fun normalize(value: String?, fallback: String): String =
        value?.lowercase()?.takeIf { it in allowed } ?: fallback

    fun defaultFor(mode: String, portrait: Boolean): String = when {
        mode == Prefs.MODE_STATIC && portrait -> FIT
        mode == Prefs.MODE_STATIC -> CROP
        else -> FIT
    }

    fun resolve(mode: String, portrait: Boolean, landscape: String?, portraitValue: String?): String {
        val fallback = defaultFor(mode, portrait)
        return normalize(if (portrait) portraitValue else landscape, fallback)
    }
}
