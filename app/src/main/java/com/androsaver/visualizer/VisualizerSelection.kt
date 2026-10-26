package com.androsaver.visualizer

internal object VisualizerSelection {
    fun initialIndex(mode: String, enabled: List<Int>): Int =
        if (mode == "random") enabled.randomOrNull() ?: 0 else enabled.firstOrNull() ?: 0
}
