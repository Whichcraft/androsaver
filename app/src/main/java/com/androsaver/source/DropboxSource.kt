package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.HttpClients
import com.androsaver.Prefs
import com.androsaver.auth.DropboxAuthManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    override fun isConfigured(): Boolean = authManager.isAuthorized()

    override suspend fun getImageUrls(): List<ImageItem> {
        val accessToken = authManager.getValidAccessToken() ?: return emptyList()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val folder = prefs.getString(Prefs.DROPBOX_FOLDER, "")?.trim() ?: ""
        // Dropbox root must be empty string "", not "/"
        val dropboxPath = when {
            folder.isEmpty() || folder == "/" -> ""
            folder.startsWith("/") -> folder
            else -> "/$folder"
        }

        return try {
            val files = listImageFiles(accessToken, dropboxPath)
            fetchTempLinks(accessToken, files)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Dropbox fetch error", e)
            emptyList()
        }
    }

    private fun listImageFiles(accessToken: String, path: String): List<Pair<String, String>> {
        val files = mutableListOf<Pair<String, String>>() // (pathLower, name)
        var cursor: String? = null
        var hasMore = true

        while (hasMore) {
            val (entries, nextCursor, more) = if (cursor == null) {
                val body = """{"path":"$path","recursive":false,"limit":2000}"""
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder")
                    .header("Authorization", "Bearer $accessToken")
                    .post(body).build()
                parseListResponse(client.newCall(request).execute().use { it.body?.string() } ?: return files)
            } else {
                val body = """{"cursor":"$cursor"}"""
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.dropboxapi.com/2/files/list_folder/continue")
                    .header("Authorization", "Bearer $accessToken")
                    .post(body).build()
                parseListResponse(client.newCall(request).execute().use { it.body?.string() } ?: return files)
            }
            files.addAll(entries)
            cursor = nextCursor
            hasMore = more
        }
        return files
    }

    private data class ListResult(
        val entries: List<Pair<String, String>>,
        val cursor: String?,
        val hasMore: Boolean
    )

    private fun parseListResponse(json: String): ListResult {
        val obj = gson.fromJson(json, JsonObject::class.java)
        val entries = mutableListOf<Pair<String, String>>()
        obj.getAsJsonArray("entries")?.forEach { el ->
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
            hasMore  = obj.get("has_more")?.asBoolean ?: false
        )
    }

    private suspend fun fetchTempLinks(
        accessToken: String,
        files: List<Pair<String, String>>
    ): List<ImageItem> = coroutineScope {
        files.map { (path, name) ->
            async {
                try {
                    val body = """{"path":"$path"}"""
                        .toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("https://api.dropboxapi.com/2/files/get_temporary_link")
                        .header("Authorization", "Bearer $accessToken")
                        .post(body).build()
                    val json = client.newCall(request).execute()
                        .use { gson.fromJson(it.body?.string(), JsonObject::class.java) }
                    val link = json.get("link")?.asString ?: return@async null
                    ImageItem(url = link, name = name)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Temp link failed for $path", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        private const val TAG = "DropboxSource"
    }
}
