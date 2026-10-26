package com.androsaver

import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSourceResult.*
import org.junit.Assert.*
import org.junit.Test

class SourceSnapshotsTest {
    private fun image(id: String, url: String = id) = ImageItem(url, stableId = id)
    @Test fun failedProviderKeepsImagesWhileHealthyProviderRefreshes() {
        val state = SourceSnapshots()
        state.update("a", Success(listOf(image("old-a"))))
        state.update("b", Success(listOf(image("old-b"))))
        state.update("b", Failure(FailureKind.NETWORK))
        state.update("a", Success(listOf(image("new-a"))))
        assertEquals(setOf("new-a", "old-b"), state.items().map { it.stableId }.toSet())
        assertEquals(FailureKind.NETWORK, state.health["b"]!!.failure)
        state.update("b", Empty)
        assertEquals(listOf("new-a"), state.items().map { it.stableId })
        state.retain(setOf("b"))
        assertTrue(state.items().isEmpty())
        assertFalse(state.health.containsKey("a"))
    }
    @Test fun partialListingKeepsOldImagesButReplacesRefreshedUrls() {
        val state = SourceSnapshots()
        state.update("a", Success(listOf(image("one", "expired"), image("two"))))
        state.update("a", Success(listOf(image("one", "fresh")), retryableFailures = true))
        assertEquals(listOf("fresh", "two"), state.items().map { it.url })
        assertTrue(state.health["a"]!!.partial)
        state.update("a", Success(listOf(image("one", "fresh"))))
        assertEquals(1, state.items().size)
        assertFalse(state.health["a"]!!.partial)
    }
}
