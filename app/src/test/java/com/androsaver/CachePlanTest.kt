package com.androsaver

import com.androsaver.source.ImageItem
import org.junit.Assert.*
import org.junit.Test

class CachePlanTest {
    @Test fun anUnavailableSourceDoesNotCauseHealthySourcesToEvictItsQuota() {
        val a = (1..300).map { ImageItem("https://a.invalid/$it", stableId = "a-$it", sourceScope = "scope-a") }
        assertEquals(100, CachePlan.select(listOf(a, emptyList())).size)
    }

    @Test fun selectionIsFairBoundedAndStableAcrossProviderAndListingOrderChanges() {
        val a = (1..300).map { ImageItem("https://a.invalid/$it", stableId = "a-$it", sourceScope = "scope-a") }
        val b = (1..300).map { ImageItem("https://b.invalid/$it", stableId = "b-$it", sourceScope = "scope-b") }
        val first = CachePlan.select(listOf(a, b))
        val next = CachePlan.select(listOf(b.reversed(), a.reversed()))
        assertEquals(first.map { it.stableId }, next.map { it.stableId })
        assertEquals(200, first.size)
        assertEquals(100, first.count { it.sourceScope == "scope-a" })
        assertEquals(100, first.count { it.sourceScope == "scope-b" })
        assertTrue(CachePlan.select(listOf(listOf(ImageItem("content://media/1")))).isEmpty())
    }
}
