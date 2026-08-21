package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.awaitResponse
import com.androsaver.stringLimited
import com.androsaver.HttpClients
import com.androsaver.Prefs
import com.androsaver.auth.DropboxAuthManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Fetches images from Dropbox via the Dropbox API v2. */
class DropboxSource(private val context: Context) : ImageSource {

    override val name = "Dropbox"

    private val client = HttpClients.standard
    private val gson = Gson()
    private val authManager = DropboxAuthManager(context)

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val maxFetch = 2000

    override fun isConfigured(): Boolean = authManager.isAuthorized()

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val accessToken = authManager.getValidAccessToken()
            ?: throw ImageSourceAuthenticationException("Dropbox authentication failed")
        val prefs = com.androsaver.Prefs.get(context)
        val folder = prefs.getString(Prefs.DROPBOX_FOLDER, "")?.trim() ?: ""
        // Dropbox root must be empty string "", not "/"
        val dropboxPath = when {
            folder.isEmpty() || folder == "/" -> ""
            folder.startsWith("/") -> folder
            else -> "/$folder"
        }

        val files = listImageFiles(accessToken, dropboxPath)
        fetchTempLinks(accessToken, files)
    }

    private suspend fun listImageFiles(accessToken: String, path: String): List<Pair<String, String>> {
        val files = mutableListOf<Pair<String, String>>() // (pathLower, name)
        var cursor: String? = null
        var hasMore = true
        var scanned = 0
        var pages = 0

        while (hasMore && files.size < maxFetch && scanned < MAX_SCANNED_ENTRIES && pages < MAX_PAGES) {
            val (entries, nextCursor, more, scannedEntries) = if (cursor == null) {
                val payload = JsonObject().apply {
                    addProperty("path", path)
                    addProperty("recursive", false)
                    addProperty("limit", 2000)
                }
                val body = gson.toJson(payload)
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder")
                    .header("Authorization", "Bearer $accessToken")
                    .post(body).build()
                parseListResponse(client.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("Dropbox listing failed with HTTP ${response.code}")
                    response.body?.stringLimited(MAX_RESPONSE_BYTES)
                } ?: return files)
            } else {
                val payload = JsonObject().apply { addProperty("cursor", cursor) }
                val body = gson.toJson(payload)
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder/continue")
                    .header("Authorization", "Bearer $accessToken")
                    .post(body).build()
                parseListResponse(client.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("Dropbox listing failed with HTTP ${response.code}")
                    response.body?.stringLimited(MAX_RESPONSE_BYTES)
                } ?: return files)
            }
            files.addAll(entries.take(maxFetch - files.size))
            scanned += scannedEntries
            pages++
            if (files.size >= maxFetch) break
            if (nextCursor != null && nextCursor == cursor) break
            cursor = nextCursor
            hasMore = more
        }
        return files
    }

    private data class ListResult(
        val entries: List<Pair<String, String>>,
        val cursor: String?,
        val hasMore: Boolean,
        val scannedEntries: Int
    )

    private fun parseListResponse(json: String): ListResult {
        val obj = gson.fromJson(json, JsonObject::class.java)
        val entries = mutableListOf<Pair<String, String>>()
        val allEntries = obj.getAsJsonArray("entries")
        allEntries?.forEach { el ->
            val entry = el.asJsonObject
            if (entry.get(".tag")?.asString == "file") {
                val name = entry.get("name")?.asString ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in imageExtensions) {
                    val path = entry.get("path_lower")?.asString ?: return@forEach
                    entries.add(path to name)
                }
            }
        }
        return ListResult(
            entries  = entries,
            cursor   = obj.get("cursor")?.asString,
            hasMore  = obj.get("has_more")?.asBoolean ?: false,
            scannedEntries = allEntries?.size() ?: 0
        )
    }

    private suspend fun fetchTempLinks(
        accessToken: String,
        files: List<Pair<String, String>>
    ): List<ImageItem> = coroutineScope {
        val semaphore = Semaphore(10)
        files.map { (path, name) ->
            async {
                semaphore.withPermit {
                    try {
                        val payload = JsonObject().apply { addProperty("path", path) }
                        val body = gson.toJson(payload)
                            .toRequestBody("application/json".toMediaType())
                        val request = Request.Builder()
                            .url("https://api.dropboxapi.com/2/files/get_temporary_link")
                            .header("Authorization", "Bearer $accessToken")
                            .post(body).build()
                        val json = client.newCall(request).awaitResponse().use { response ->
                            if (!response.isSuccessful) return@withPermit null
                            gson.fromJson(response.body?.stringLimited(MAX_RESPONSE_BYTES), JsonObject::class.java)
                        }
                        val link = json.get("link")?.asString ?: return@withPermit null
                        ImageItem(url = link, name = name, stableId = "dropbox:${path.lowercase()}")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Temp link failed for $path", e)
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        private const val TAG = "DropboxSource"
        private const val MAX_PAGES = 100
        private const val MAX_SCANNED_ENTRIES = 20_000
        private const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    }
}
