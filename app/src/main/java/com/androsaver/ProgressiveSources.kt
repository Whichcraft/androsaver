package com.androsaver

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Deliver each useful source immediately; parent cancellation owns every fetch. */
internal object ProgressiveSources {
    suspend fun <S, T> load(
        sources: List<S>,
        fetch: suspend (S) -> List<T>?,
        onItems: suspend (S, List<T>) -> Unit
    ) = coroutineScope {
        sources.forEach { source ->
            launch {
                val items = fetch(source)
                if (!items.isNullOrEmpty()) onItems(source, items)
            }
        }
    }
}
