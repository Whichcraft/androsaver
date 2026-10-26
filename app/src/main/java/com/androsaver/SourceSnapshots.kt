package com.androsaver

import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSourceResult

/** Main-thread source state: failure preserves the last useful snapshot. */
internal class SourceSnapshots(private val now: () -> Long = System::currentTimeMillis) {
    data class Health(val failure: ImageSourceResult.FailureKind? = null, val partial: Boolean = false, val empty: Boolean = false, val lastSuccessAt: Long? = null, val lastAttemptAt: Long? = null)
    private val bindings = mutableMapOf<String, String?>()
    private val freshForCache = linkedMapOf<String, List<ImageItem>>()
    private val snapshots = linkedMapOf<String, List<ImageItem>>()
    val health = linkedMapOf<String, Health>()
    fun retain(names: Set<String>) {
        snapshots.keys.retainAll(names)
        health.keys.retainAll(names)
        bindings.keys.retainAll(names)
        freshForCache.keys.retainAll(names)
    }
    fun bind(name: String, scope: String?) {
        if (bindings.containsKey(name) && bindings[name] != scope) { snapshots.remove(name); health.remove(name); freshForCache.remove(name) }
        bindings[name] = scope
    }
    fun emptyScopes(): Set<String> = health.filterValues { it.empty }.keys.mapNotNull { bindings[it] }.toSet()
    fun groups(): List<List<ImageItem>> = bindings.filterValues { it != null }.keys.map { freshForCache[it].orEmpty() }
    fun update(name: String, result: ImageSourceResult) {
        when (result) {
            is ImageSourceResult.Success -> {
                freshForCache[name] = result.items
                // A partial listing is not authoritative about missing images.
                snapshots[name] = if (result.retryableFailures)
                    (result.items + snapshots[name].orEmpty()).distinctBy { it.stableId }
                else result.items.distinctBy { it.stableId }
                health[name] = Health(partial = result.retryableFailures, lastSuccessAt = now(), lastAttemptAt = now())
            }
            ImageSourceResult.Empty -> { freshForCache.remove(name); snapshots[name] = emptyList(); health[name] = Health(empty = true, lastSuccessAt = now(), lastAttemptAt = now()) }
            is ImageSourceResult.Failure -> {
                freshForCache.remove(name)
                health[name] = Health(failure = result.kind, lastSuccessAt = health[name]?.lastSuccessAt, lastAttemptAt = now())
            }
        }
    }
    fun items(): List<ImageItem> = snapshots.values.flatten().distinctBy { it.stableId }
    fun sourceNames(): Map<String, String> = buildMap {
        snapshots.forEach { (name, items) -> items.forEach { put(it.stableId, name) } }
    }
    fun clear() { snapshots.clear(); health.clear(); bindings.clear(); freshForCache.clear() }
}
