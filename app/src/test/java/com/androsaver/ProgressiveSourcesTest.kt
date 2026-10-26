package com.androsaver

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProgressiveSourcesTest {
    @Test fun fastSourceIsDeliveredBeforeSlowSourceCompletes() = runBlocking {
        withTimeout(2000) {
            val releaseSlow = CompletableDeferred<Unit>()
            val firstReady = CompletableDeferred<Unit>()
            val displayed = mutableListOf<String>()
            val loading = launch {
                ProgressiveSources.load(listOf("slow", "fast"), fetch = { source ->
                    if (source == "slow") releaseSlow.await()
                    listOf(source)
                }, onItems = { _, items ->
                    displayed.addAll(items)
                    firstReady.complete(Unit)
                })
            }
            firstReady.await()
            assertEquals(listOf("fast"), displayed)
            assertFalse(loading.isCompleted)
            releaseSlow.complete(Unit)
            loading.join()
            assertEquals(listOf("fast", "slow"), displayed)
        }
    }

    @Test fun cancellationStopsPendingSourcesAndEmptySourcesDoNotStartDisplay() = runBlocking {
        withTimeout(2000) {
            val pending = CompletableDeferred<Unit>()
            val delivered = mutableListOf<String>()
            val loading = launch {
                ProgressiveSources.load(listOf("empty", "offline", "pending"), fetch = { source ->
                    when (source) {
                        "empty" -> emptyList<String>()
                        "offline" -> null
                        else -> { pending.complete(Unit); awaitCancellation() }
                    }
                }, onItems = { _, items -> delivered.addAll(items) })
            }
            pending.await()
            loading.cancelAndJoin()
            assertTrue(delivered.isEmpty())
            assertTrue(loading.isCancelled)
        }
    }
}
