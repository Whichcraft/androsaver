package com.androsaver.source

import com.google.gson.JsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Validate every bearer-authenticated page before giving it to the fetcher. */
internal class GraphPages(
    private val maxPages: Int = 40,
    private val maxScanned: Int = 20_000,
    private val maxImages: Int = 2000
) {
    private fun trusted(raw: String): HttpUrl {
        val url = raw.toHttpUrlOrNull()
        require(url != null && url.scheme == "https" && url.host == "graph.microsoft.com" &&
            url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
            "Untrusted Graph pagination URL"
        }
        return url
    }

    suspend fun list(start: String, fetch: suspend (HttpUrl) -> JsonObject): List<ImageItem> {
        var next: String? = start
        val visited = hashSetOf<HttpUrl>()
        val ids = hashSetOf<String>()
        val images = mutableListOf<ImageItem>()
        var scanned = 0
        var pages = 0
        while (next != null && pages < maxPages && scanned < maxScanned && images.size < maxImages) {
            currentCoroutineContext().ensureActive()
            val url = trusted(next)
            require(visited.add(url)) { "Repeated Graph pagination URL" }
            val page = fetch(url)
            pages++
            val values = page.get("value")?.takeIf { it.isJsonArray }?.asJsonArray
            if (values != null) for (entry in values) {
                currentCoroutineContext().ensureActive()
                if (scanned >= maxScanned || images.size >= maxImages) break
                scanned++
                if (!entry.isJsonObject) continue
                val obj = entry.asJsonObject
                val file = obj.get("file")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val mime = file.get("mimeType")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                if (!mime.startsWith("image/")) continue
                fun text(key: String) = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                val id = text("id")?.takeIf { it.isNotBlank() } ?: continue
                val name = text("name") ?: continue
                val download = text("@microsoft.graph.downloadUrl") ?: continue
                if (ids.add(id)) images += ImageItem(download, name, stableId = "onedrive:$id")
            }
            next = page.get("@odata.nextLink")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        }
        return images
    }
}
