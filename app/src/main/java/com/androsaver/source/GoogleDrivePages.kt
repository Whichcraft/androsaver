package com.androsaver.source

import com.google.gson.JsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bound work even when pages contain no usable files or repeat a cursor. */
internal class GoogleDrivePages(
    private val maxItems: Int = 2000,
    private val maxPages: Int = 20,
    private val maxScanned: Int = 20_000
) {
    data class Entry(val id: String, val name: String, val version: String? = null)

    suspend fun list(fetch: suspend (String?) -> JsonObject): List<Entry> {
        val items = mutableListOf<Entry>()
        val ids = hashSetOf<String>()
        val cursors = hashSetOf<String>()
        var cursor: String? = null
        var pages = 0
        var scanned = 0
        while (pages < maxPages && scanned < maxScanned && items.size < maxItems) {
            currentCoroutineContext().ensureActive()
            if (cursor != null && !cursors.add(cursor)) break
            val page = fetch(cursor)
            pages++
            val files = page.get("files")?.takeIf { it.isJsonArray }?.asJsonArray
            if (files != null) for (file in files) {
                currentCoroutineContext().ensureActive()
                if (scanned >= maxScanned || items.size >= maxItems) break
                scanned++
                if (!file.isJsonObject) continue
                val obj = file.asJsonObject
                val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.isNotBlank() } ?: continue
                val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val version = obj.get("modifiedTime")?.takeIf { it.isJsonPrimitive }?.asString
                if (ids.add(id)) items += Entry(id, name, version)
            }
            cursor = page.get("nextPageToken")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() } ?: break
        }
        return items
    }
}
