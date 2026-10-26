package com.androsaver

import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSourceResult

/** Main-thread source state: failure preserves the last useful snapshot. */
internal class SourceSnapshots {
    data class Health(val failure: ImageSourceResult.FailureKind? = null, val partial: Boolean = false, val empty: Boolean = false)
    private val snapshots = linkedMapOf<String, List<ImageItem>>()
    val health = linkedMapOf<String, Health>()
    fun retain(names: Set<String>) {
        snapshots.keys.retainAll(names)
        health.keys.retainAll(names)
    }
    fun update(name: String, result: ImageSourceResult) {
        when (result) {
            is ImageSourceResult.Success -> {
                // A partial listing is not authoritative about missing images.
                snapshots[name] = if (result.retryableFailures)
                    (result.items + snapshots[name].orEmpty()).distinctBy { it.stableId }
                else result.items.distinctBy { it.stableId }
                health[name] = Health(partial = result.retryableFailures)
            }
            ImageSourceResult.Empty -> { snapshots[name] = emptyList(); health[name] = Health(empty = true) }
            is ImageSourceResult.Failure -> health[name] = Health(failure = result.kind)
        }
    }
    fun items(): List<ImageItem> = snapshots.values.flatten().distinctBy { it.stableId }
    fun sourceNames(): Map<String, String> = buildMap {
        snapshots.forEach { (name, items) -> items.forEach { put(it.stableId, name) } }
    }
    fun clear() { snapshots.clear(); health.clear() }
}
