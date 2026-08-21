package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.awaitResponse
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.androsaver.HttpClients
import okhttp3.FormBody
import okhttp3.Request
import java.net.URLEncoder

class GoogleDriveSource(private val context: Context) : ImageSource {

    override val name = "Google Drive"

    private val client = HttpClients.standard
    private val gson = Gson()

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val accessToken = refreshAccessTokenSilently() ?: run {
            throw ImageSourceAuthenticationException("Google Drive authentication failed")
        }

        val prefs = com.androsaver.Prefs.get(context)
        val folderId = prefs.getString(Prefs.GOOGLE_FOLDER_ID, "root")?.ifEmpty { "root" } ?: "root"

        val query = URLEncoder.encode(
            "mimeType contains 'image/' and '$folderId' in parents and trashed = false",
            "UTF-8"
        )
        val baseUrl = "https://www.googleapis.com/drive/v3/files" +
                "?q=$query&fields=files(id,name,mimeType),nextPageToken&pageSize=1000"

        val items = mutableListOf<ImageItem>()
            var pageToken: String? = null
            val maxFetch = 2000
            do {
                val url = if (pageToken != null) "$baseUrl&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else baseUrl
                val response = client.newCall(
                    Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").build()
                ).awaitResponse()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw java.io.IOException("Google Drive listing failed with HTTP $code")
                }
                val json = response.use { gson.fromJson(it.body?.string(), JsonObject::class.java) }
                val files = json.getAsJsonArray("files")
                if (files != null) {
                    for (file in files) {
                        if (items.size >= maxFetch) break
                        val obj = file.asJsonObject
                        val fileId = obj.get("id")?.asString ?: continue
                        val name = obj.get("name")?.asString ?: continue
                        items.add(ImageItem(
                            url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
                            name = name,
                            headers = mapOf("Authorization" to "Bearer $accessToken"),
                            stableId = "google:$fileId"
                        ))
                    }
                }
                if (items.size >= maxFetch) break
                pageToken = json.get("nextPageToken")?.asString
            } while (pageToken != null)
        items
    }

    internal suspend fun refreshAccessTokenSilently(): String? = refreshMutex.withLock {
        val prefs = com.androsaver.Prefs.get(context)
        val refreshToken = prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null)
            ?: return@withLock null
        val clientId = prefs.getString(Prefs.GOOGLE_CLIENT_ID, null)
            ?: return@withLock null
        val clientSecret = prefs.getString(Prefs.GOOGLE_CLIENT_SECRET, null)
            ?: return@withLock null

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        try {
            val json = client.newCall(request).awaitResponse().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }
            val token = json.get("access_token")?.asString
            if (token != null) {
                prefs.edit().putString(Prefs.GOOGLE_ACCESS_TOKEN, token).apply()
            }
            token
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "GoogleDriveSource"
        private val refreshMutex = Mutex()
    }
}
