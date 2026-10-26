package com.androsaver

import com.androsaver.source.GraphPages
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GraphPagesTest {
    private val start = "https://graph.microsoft.com/v1.0/me/drive/root/children"
    private fun page(next: String? = null) = JsonObject().apply { next?.let { addProperty("@odata.nextLink", it) } }

    @Test fun untrustedNextPagesAreRejectedBeforeTheNextAuthenticatedFetch() = runBlocking {
        for (next in listOf("http://graph.microsoft.com/v1.0/me", "https://example.invalid/page",
            "https://graph.microsoft.com:444/page", "https://user:placeholder@graph.microsoft.com/page",
            "$start#fragment", "not a URL")) {
            var requests = 0
            val result = runCatching { GraphPages().list(start) { requests++; page(next) } }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(1, requests)
        }
    }

    @Test fun repeatedPagesAreRejectedAndOpaqueCursorEncodingIsPreserved() = runBlocking {
        var requests = 0
        val repeated = runCatching { GraphPages().list(start) { requests++; page(start) } }
        assertTrue(repeated.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, requests)
        val next = "$start?%24skiptoken=opaque%2Bcursor%3D"
        val visited = mutableListOf<String>()
        GraphPages().list(start) {
            visited += it.toString()
            page(if (visited.size == 1) next else null)
        }
        assertEquals(listOf(start, next), visited)
    }

    @Test fun imageRowsAndEmptyPagesRemainBounded() = runBlocking {
        var requests = 0
        GraphPages(maxPages = 2).list(start) { requests++; page("$start?page=$requests") }
        assertEquals(2, requests)
        val images = GraphPages(maxImages = 1).list(start) {
            JsonParser.parseString("""{"value":[{"id":"1","name":"photo","eTag":"revision-two","file":{"mimeType":"image/jpeg"},"@microsoft.graph.downloadUrl":"https://example.invalid/photo"}]}""").asJsonObject
        }
        assertEquals("onedrive:1", images.single().stableId)
        assertEquals("revision-two", images.single().contentVersion)
        assertTrue(images.single().headers.isEmpty())
    }
}
