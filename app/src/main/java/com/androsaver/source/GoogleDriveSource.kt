package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class GoogleDriveSource(private val context: Context) : ImageSource {

    override val name = "Google Drive"

    private val client = OkHttpClient()
    private val gson = Gson()

    override fun isConfigured(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        val accessToken = refreshAccessTokenSilently() ?: run {
            Log.w(TAG, "No valid access token")
            return@withContext emptyList()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val folderId = prefs.getString(Prefs.GOOGLE_FOLDER_ID, "root")?.ifEmpty { "root" } ?: "root"

        val query = URLEncoder.encode(
            "mimeType contains 'image/' and '$folderId' in parents and trashed = false",
            "UTF-8"
        )
        val url = "https://www.googleapis.com/drive/v3/files" +
                "?q=$query&fields=files(id,name,mimeType)&pageSize=1000"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "List files failed: ${response.code}")
                return@withContext emptyList()
            }
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val files = json.getAsJsonArray("files") ?: return@withContext emptyList()

            files.map { file ->
                val obj = file.asJsonObject
                val fileId = obj.get("id").asString
                ImageItem(
                    url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
                    name = obj.get("name").asString,
                    headers = mapOf("Authorization" to "Bearer $accessToken")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing Drive files", e)
            emptyList()
        }
    }

    internal fun refreshAccessTokenSilently(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val refreshToken = prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null) ?: return null
        val clientId = prefs.getString(Prefs.GOOGLE_CLIENT_ID, null) ?: return null
        val clientSecret = prefs.getString(Prefs.GOOGLE_CLIENT_SECRET, null) ?: return null

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

        return try {
            val response = client.newCall(request).execute()
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val token = json.get("access_token")?.asString
            if (token != null) {
                prefs.edit().putString(Prefs.GOOGLE_ACCESS_TOKEN, token).apply()
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "GoogleDriveSource"
    }
}
