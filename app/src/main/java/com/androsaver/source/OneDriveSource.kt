package com.androsaver.source

import com.androsaver.auth.OAuthAccounts
import android.content.Context
import android.util.Log
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
    // A validated page must not silently redirect into an unvalidated request.
    private val listingClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val gson = Gson()

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        if (!isConfigured()) throw ImageSourceAuthenticationException("Account is disconnected")
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

        GraphPages().list(startUrl) { page ->
            listingClient.newCall(
                Request.Builder().url(page).header("Authorization", "Bearer $accessToken").build()
            ).awaitResponse().use { response ->
                if (!response.isSuccessful) throw ImageSourceHttpException(response.code)
                gson.fromJson(response.body?.stringLimited(MAX_RESPONSE_BYTES), JsonObject::class.java)
                    ?: throw java.io.IOException("Empty OneDrive listing")
            }
        }
    }

    internal suspend fun refreshAccessTokenSilently(): String? = refreshMutex.withLock {
        val prefs = com.androsaver.Prefs.get(context)
        val account = OAuthAccounts.capture(prefs, "onedrive")
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
            ).awaitResponse().use { response ->
                if (!response.isSuccessful && com.androsaver.PrefetchRetryPolicy.http(response.code)) {
                    throw ImageSourceHttpException(response.code)
                }
                gson.fromJson(response.body?.stringLimited(64L * 1024), JsonObject::class.java)
            }

            val token = json.get("access_token")?.asString
            if (token != null && !OAuthAccounts.publish(prefs, account) {
                val edit = prefs.edit().putString(Prefs.ONEDRIVE_ACCESS_TOKEN, token)
                json.get("refresh_token")?.asString?.let { edit.putString(Prefs.ONEDRIVE_REFRESH_TOKEN, it) }
                edit.apply()
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
        private const val TAG = "OneDriveSource"
        private const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
        private val refreshMutex = Mutex()
    }
}
