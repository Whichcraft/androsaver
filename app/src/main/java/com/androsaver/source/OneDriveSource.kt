package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.awaitResponse
import com.androsaver.stringLimited
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

/** Fetches images from OneDrive (personal or work) via Microsoft Graph API. */
class OneDriveSource(private val context: Context) : ImageSource {

    override val name = "OneDrive"

    private val client = HttpClients.standard
    private val gson = Gson()

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        val accessToken = refreshAccessTokenSilently() ?: run {
            throw ImageSourceAuthenticationException("OneDrive authentication failed")
        }

        val prefs = com.androsaver.Prefs.get(context)
        val folder = prefs.getString(Prefs.ONEDRIVE_FOLDER, "")?.trim() ?: ""

        // Build the children listing URL; root or path-based
        val childrenBase = if (folder.isEmpty() || folder == "/") {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            val encodedPath = folder.trimStart('/').split("/")
                .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/children"
        }
        val startUrl = "$childrenBase?\$select=id,name,file,@microsoft.graph.downloadUrl&\$top=1000"

        val items = mutableListOf<ImageItem>()
            var url: String? = startUrl
            val maxFetch = 2000
            var scanned = 0
            var pages = 0

            while (url != null && items.size < maxFetch && scanned < MAX_SCANNED_ENTRIES && pages < MAX_PAGES) {
                val json = client.newCall(
                    Request.Builder().url(url)
                        .header("Authorization", "Bearer $accessToken").build()
                ).awaitResponse().use { resp ->
                    if (!resp.isSuccessful) {
                        throw java.io.IOException("OneDrive listing failed with HTTP ${resp.code}")
                    }
                    gson.fromJson(resp.body?.stringLimited(MAX_RESPONSE_BYTES), JsonObject::class.java)
                }

                pages++
                val valueArray = json.getAsJsonArray("value")
                if (valueArray != null) {
                    scanned += valueArray.size()
                    for (el in valueArray) {
                        if (items.size >= maxFetch) break
                        val obj      = el.asJsonObject
                        val fileObj  = obj.getAsJsonObject("file") ?: continue
                        val mime     = fileObj.get("mimeType")?.asString ?: continue
                        if (!mime.startsWith("image/")) continue
                        val name     = obj.get("name")?.asString ?: continue
                        val id       = obj.get("id")?.asString ?: continue
                        // @microsoft.graph.downloadUrl is a pre-authenticated temporary URL — no auth header needed for Glide
                        val dlUrl    = obj.get("@microsoft.graph.downloadUrl")?.asString ?: continue
                        items.add(ImageItem(url = dlUrl, name = name, stableId = "onedrive:$id"))
                    }
                }

                url = if (items.size >= maxFetch) null else json.get("@odata.nextLink")?.asString
            }
        items
    }

    internal suspend fun refreshAccessTokenSilently(): String? = refreshMutex.withLock {
        val prefs = com.androsaver.Prefs.get(context)
        val refreshToken = prefs.getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null)
            ?: return@withLock null
        val clientId = prefs.getString(Prefs.ONEDRIVE_CLIENT_ID, null)
            ?: return@withLock null

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", "Files.Read offline_access")
            .build()

        try {
            val json = client.newCall(
                Request.Builder()
                    .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .post(body).build()
            ).awaitResponse().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            val token = json.get("access_token")?.asString
            if (token != null) {
                val edit = prefs.edit().putString(Prefs.ONEDRIVE_ACCESS_TOKEN, token)
                json.get("refresh_token")?.asString?.let { edit.putString(Prefs.ONEDRIVE_REFRESH_TOKEN, it) }
                edit.apply()
            }
            token
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "OneDriveSource"
        private const val MAX_PAGES = 40
        private const val MAX_SCANNED_ENTRIES = 20_000
        private const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
        private val refreshMutex = Mutex()
    }
}
