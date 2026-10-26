package com.androsaver.source

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Metadata search shared by library/album listing; supports page and v3.2 cursor responses. */
internal object ImmichPages {
    data class Asset(val id: String, val name: String, val version: String?)
    suspend fun list(album: String?, fetch: suspend (JsonObject) -> JsonObject): List<Asset> {
        val images = linkedMapOf<String, Asset>()
        val seenTokens = mutableSetOf<String>()
        var page = 1
        var cursor: String? = null
        var scanned = 0
        repeat(40) {
            currentCoroutineContext().ensureActive()
            val payload = JsonObject().apply {
                addProperty("size", 500)
                addProperty("type", "IMAGE")
                addProperty("withDeleted", false)
                if (album.isNullOrBlank()) addProperty("visibility", "timeline")
                if (cursor != null) addProperty("cursor", cursor) else addProperty("page", page)
                if (!album.isNullOrBlank()) add("albumIds", JsonArray().apply { add(album) })
            }
            val response = fetch(payload)
            val assets = response.getAsJsonObject("assets") ?: throw JsonParseException("Missing Immich assets result")
            val items = assets.getAsJsonArray("items") ?: throw JsonParseException("Missing Immich asset items")
            if (items.size() == 0) return images.values.toList()
            val before = images.size
            for (element in items) {
                currentCoroutineContext().ensureActive()
                if (++scanned > 20_000) return images.values.toList()
                val asset = element.asJsonObject
                if (asset.get("type")?.asString != "IMAGE" || asset.get("isTrashed")?.asBoolean == true) continue
                val id = asset.get("id")?.asString?.takeIf { it.isNotBlank() }
                    ?: throw JsonParseException("Missing Immich asset ID")
                images[id] = Asset(id, asset.get("originalFileName")?.asString ?: id,
                    asset.get("updatedAt")?.takeUnless { it.isJsonNull }?.asString)
                if (images.size == 2000) return images.values.toList()
            }
            val nextCursor = assets.get("nextCursor")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            val nextPage = assets.get("nextPage")?.takeUnless { it.isJsonNull }?.asString?.toIntOrNull()
            val token = when {
                nextCursor != null -> "cursor:$nextCursor"
                nextPage != null && nextPage > page -> "page:$nextPage"
                else -> return images.values.toList()
            }
            if (!seenTokens.add(token) || (images.size == before && items.all { e -> e.asJsonObject.get("type")?.asString == "IMAGE" })) {
                return images.values.toList()
            }
            cursor = nextCursor
            if (nextPage != null) page = nextPage
        }
        return images.values.toList()
    }
}
