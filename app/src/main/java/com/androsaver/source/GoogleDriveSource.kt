package com.androsaver.source

import com.androsaver.auth.OAuthAccounts
import android.content.Context
import android.util.Log
import com.androsaver.BuildConfig
import com.androsaver.stringLimited
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
        if (!isConfigured()) throw ImageSourceAuthenticationException("Account is disconnected")
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
                "?q=$query&fields=files(id,name,mimeType,modifiedTime),nextPageToken&pageSize=1000"

        GoogleDrivePages().list { pageToken ->
            val url = if (pageToken != null) {
                "$baseUrl&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
            } else baseUrl
            client.newCall(
                Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").build()
            ).awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    throw ImageSourceHttpException(response.code)
                }
                gson.fromJson(response.body?.stringLimited(MAX_RESPONSE_BYTES), JsonObject::class.java)
                    ?: throw java.io.IOException("Empty Google Drive listing")
            }
        }.map { file ->
            ImageItem(
                url = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
                name = file.name,
                headers = mapOf("Authorization" to "Bearer $accessToken"),
                stableId = "google:${file.id}",
                contentVersion = file.version
            )
        }
    }

    internal suspend fun refreshAccessTokenSilently(): String? = refreshMutex.withLock {
        val prefs = com.androsaver.Prefs.get(context)
        val account = OAuthAccounts.capture(prefs, "google")
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
            val json = client.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful && com.androsaver.PrefetchRetryPolicy.http(response.code)) {
                    throw ImageSourceHttpException(response.code)
                }
                gson.fromJson(response.body?.stringLimited(MAX_TOKEN_RESPONSE_BYTES), JsonObject::class.java)
            }
            val token = json.get("access_token")?.asString
            if (token != null && !OAuthAccounts.publish(prefs, account) {
                prefs.edit().putString(Prefs.GOOGLE_ACCESS_TOKEN, token).apply()
            }) return@withLock null
            token
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is java.io.IOException || e is com.google.gson.JsonParseException) throw e
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "GoogleDriveSource"
        private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
        private const val MAX_TOKEN_RESPONSE_BYTES = 64L * 1024
        private val refreshMutex = Mutex()
    }
}
