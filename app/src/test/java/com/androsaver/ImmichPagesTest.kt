package com.androsaver

import com.androsaver.source.ImmichPages
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImmichPagesTest {
    private fun fixture(name: String) = JsonParser.parseString(
        requireNotNull(javaClass.getResource("/immich/$name-search.json")).readText()).asJsonObject.getAsJsonArray("pages")
    @Test fun v2AlbumSearchUsesTheMetadataContractAndDeduplicatesImages() = runBlocking {
        val pages = fixture("v2")
        val requests = mutableListOf<JsonObject>()
        val images = ImmichPages.list("placeholder-album") { payload ->
            requests += payload
            pages[requests.size - 1].asJsonObject
        }
        assertEquals(listOf("one", "two"), images.map { it.id })
        assertEquals("placeholder-album", requests[0].getAsJsonArray("albumIds")[0].asString)
        assertEquals("IMAGE", requests[0].get("type").asString)
        assertEquals(2, requests[1].get("page").asInt)
    }
    @Test fun v3LibrarySearchPreservesOpaqueCursorAndHasNoAlbumFilter() = runBlocking {
        val pages = fixture("v3")
        val requests = mutableListOf<JsonObject>()
        val images = ImmichPages.list(null) { requests += it; pages[requests.size - 1].asJsonObject }
        assertEquals(2, images.size)
        assertEquals("opaque+/=cursor", requests[1].get("cursor").asString)
        assertFalse(requests[1].has("page"))
        assertFalse(requests[0].has("albumIds"))
    }
    @Test fun repeatedPagesAreBoundedAndMalformedResultsAreNotEmptyLibraries() = runBlocking {
        var calls = 0
        val page = JsonParser.parseString("""{"assets":{"items":[{"id":"one","type":"IMAGE"}],"nextCursor":"repeat"}}""").asJsonObject
        assertEquals(1, ImmichPages.list(null) { calls++; page }.size)
        assertEquals(2, calls)
        try { ImmichPages.list(null) { JsonObject() }; fail("Missing result accepted") }
        catch (_: JsonParseException) { }
    }
}
