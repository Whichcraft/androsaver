package com.androsaver

import com.androsaver.source.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SourceRefreshCoordinatorTest {
    @Test fun healthyRefreshIsAppliedBeforeSlowProviderAndFailurePreservesOldItems() = runBlocking {
        withTimeout(2000) {
            val slow = CompletableDeferred<Unit>()
            val fastApplied = CompletableDeferred<Unit>()
            val state = SourceSnapshots()
            state.update("slow", ImageSourceResult.Success(listOf(ImageItem("old-slow"))))
            val coordinator = SourceRefreshCoordinator(state)
            fun source(sourceName: String) = object : ImageSource {
                override val name = sourceName
                override fun isConfigured() = true
                override suspend fun getImageUrls(): List<ImageItem> {
                    if (name == "slow") { slow.await(); throw java.io.IOException("offline") }
                    return listOf(ImageItem("new-fast"))
                }
            }
            val refresh = launch {
                coordinator.refresh(listOf(source("slow"), source("fast")), onUpdated = {
                    if (state.items().any { it.url == "new-fast" }) fastApplied.complete(Unit)
                }, onItems = { _, _ -> })
            }
            fastApplied.await()
            assertFalse(refresh.isCompleted)
            assertEquals(setOf("old-slow", "new-fast"), state.items().map { it.url }.toSet())
            slow.complete(Unit)
            refresh.join()
            assertEquals(setOf("old-slow", "new-fast"), state.items().map { it.url }.toSet())
        }
    }
}
