package com.androsaver.visualizer

internal object VisualizerSelection {
    fun enabled(stored:Set<String>?,known:Set<String>?,all:Set<String>):Set<String> =
        if(stored.isNullOrEmpty()) all else ((stored + (all-(known?:all))).intersect(all)).ifEmpty { all }

    fun initialIndex(mode: String, enabled: List<Int>): Int =
        if (mode == "random") enabled.randomOrNull() ?: 0 else enabled.firstOrNull() ?: 0
}
