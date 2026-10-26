package com.androsaver

import com.androsaver.source.ImageItem
import java.security.MessageDigest

/** Stable order and one shared capacity prevent providers repeatedly evicting each other. */
internal object CachePlan {
    fun select(groups: List<List<ImageItem>>, limit: Int = 200): List<ImageItem> {
        val ordered = groups.filter { group -> group.isEmpty() || group.any { it.url.startsWith("https://") || it.url.startsWith("http://") } }
            .map { group -> group.filter { it.url.startsWith("https://") || it.url.startsWith("http://") } }
            .sortedBy { group -> group.minOfOrNull { it.sourceScope ?: it.stableId } ?: "~unavailable" }
        if (ordered.isEmpty()) return emptyList()
        val queues = ordered.mapIndexed { index, group ->
            val quota = limit / ordered.size + if (index < limit % ordered.size) 1 else 0
            group.distinctBy { it.stableId }.map { it to rank(it.stableId) }.sortedBy { it.second }
                .take(quota).map { it.first }.iterator()
        }
        val result = mutableListOf<ImageItem>()
        val seen = mutableSetOf<String>()
        while (result.size < limit && queues.any { it.hasNext() }) {
            queues.forEach { queue ->
                if (result.size < limit && queue.hasNext()) {
                    val item = queue.next()
                    if (seen.add(item.stableId)) result += item
                }
            }
        }
        return result
    }
    private fun rank(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
