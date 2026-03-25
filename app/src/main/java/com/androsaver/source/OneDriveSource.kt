package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Fetches images from OneDrive (personal or work) via Microsoft Graph API. */
class OneDriveSource(private val context: Context) : ImageSource {

    override val name = "OneDrive"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override fun isConfigured(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        val accessToken = refreshAccessTokenSilently() ?: run {
            if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "No valid access token")
            return@withContext emptyList()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val folder = prefs.getString(Prefs.ONEDRIVE_FOLDER, "")?.trim() ?: ""

        // Build the children listing URL; root or path-based
        val childrenBase = if (folder.isEmpty() || folder == "/") {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            val encodedPath = folder.trimStart('/').split("/")
                .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/children"
        }
        val startUrl = "$childrenBase?\$select=name,file,@microsoft.graph.downloadUrl&\$top=1000"

        try {
            val items = mutableListOf<ImageItem>()
            var url: String? = startUrl

            while (url != null) {
                val json = client.newCall(
                    Request.Builder().url(url)
                        .header("Authorization", "Bearer $accessToken").build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "List files failed: ${resp.code}")
                        return@withContext emptyList()
                    }
                    gson.fromJson(resp.body?.string(), JsonObject::class.java)
                }

                json.getAsJsonArray("value")?.mapNotNullTo(items) { el ->
                    val obj      = el.asJsonObject
                    val fileObj  = obj.getAsJsonObject("file") ?: return@mapNotNullTo null
                    val mime     = fileObj.get("mimeType")?.asString ?: return@mapNotNullTo null
                    if (!mime.startsWith("image/")) return@mapNotNullTo null
                    val name     = obj.get("name")?.asString ?: return@mapNotNullTo null
                    // @microsoft.graph.downloadUrl is a pre-authenticated temporary URL — no auth header needed for Glide
                    val dlUrl    = obj.get("@microsoft.graph.downloadUrl")?.asString ?: return@mapNotNullTo null
                    ImageItem(url = dlUrl, name = name)
                }

                url = json.get("@odata.nextLink")?.asString
            }
            items
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Error listing OneDrive files", e)
            emptyList()
        }
    }

    internal fun refreshAccessTokenSilently(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val refreshToken = prefs.getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null) ?: return null
        val clientId     = prefs.getString(Prefs.ONEDRIVE_CLIENT_ID, null) ?: return null

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", "Files.Read offline_access")
            .build()

        return try {
            val json = client.newCall(
                Request.Builder()
                    .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .post(body).build()
            ).execute().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            val token = json.get("access_token")?.asString
            if (token != null) {
                val edit = prefs.edit().putString(Prefs.ONEDRIVE_ACCESS_TOKEN, token)
                json.get("refresh_token")?.asString?.let { edit.putString(Prefs.ONEDRIVE_REFRESH_TOKEN, it) }
                edit.apply()
            }
            token
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "OneDriveSource"
    }
}
