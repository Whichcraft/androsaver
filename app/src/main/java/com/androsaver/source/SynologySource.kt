package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.HttpClients
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

class SynologySource(private val context: Context) : ImageSource {

    override val name = "Synology NAS"

    // Allow self-signed certificates common on local NAS devices
    private val client = HttpClients.trustAll
    private val gson = Gson()

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

    override fun isConfigured(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getString(Prefs.SYNOLOGY_HOST, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val host = prefs.getString(Prefs.SYNOLOGY_HOST, null) ?: return@withContext emptyList()
        val port = prefs.getString(Prefs.SYNOLOGY_PORT, "5000") ?: "5000"
        val useHttps = prefs.getBoolean(Prefs.SYNOLOGY_USE_HTTPS, false)
        val username = prefs.getString(Prefs.SYNOLOGY_USERNAME, null) ?: return@withContext emptyList()
        val password = prefs.getString(Prefs.SYNOLOGY_PASSWORD, null) ?: return@withContext emptyList()
        val folder = prefs.getString(Prefs.SYNOLOGY_FOLDER, "/photos")?.ifEmpty { "/photos" } ?: "/photos"

        val scheme = if (useHttps) "https" else "http"
        val baseUrl = "$scheme://$host:$port"

        val sid = login(baseUrl, username, password) ?: return@withContext emptyList()
        // Don't logout here — the SID is embedded in image URLs, and Glide loads images
        // after getImageUrls() returns.  Let the Synology session expire naturally (~30 min).
        listImages(baseUrl, folder, sid)
    }

    private fun login(baseUrl: String, username: String, password: String): String? {
        val user = URLEncoder.encode(username, "UTF-8")
        val pass = URLEncoder.encode(password, "UTF-8")
        val url = "$baseUrl/webapi/auth.cgi?api=SYNO.API.Auth&version=3&method=login" +
                "&account=$user&passwd=$pass&session=AndroSaver&format=sid"

        return try {
            val json = client.newCall(Request.Builder().url(url).build()).execute()
                .use { gson.fromJson(it.body?.string(), JsonObject::class.java) }
            if (json.get("success")?.asBoolean == true) {
                json.getAsJsonObject("data")?.get("sid")?.asString
            } else {
                if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Login failed: ${json.get("error")}")
                null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Login error", e)
            null
        }
    }

    private fun listImages(baseUrl: String, folder: String, sid: String): List<ImageItem> {
        val encodedFolder = URLEncoder.encode(folder, "UTF-8")
        val url = "$baseUrl/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list" +
                "&folder_path=$encodedFolder&filetype=file&_sid=$sid"

        return try {
            val json = client.newCall(Request.Builder().url(url).build()).execute()
                .use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            if (json.get("success")?.asBoolean != true) {
                if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "List files failed: $json")
                return emptyList()
            }

            val files = json.getAsJsonObject("data")?.getAsJsonArray("files") ?: return emptyList()

            files.mapNotNull { file ->
                val obj = file.asJsonObject
                val name = obj.get("name")?.asString ?: return@mapNotNull null
                if (name.substringAfterLast('.', "").lowercase() !in imageExtensions) return@mapNotNull null

                val path = obj.get("path")?.asString ?: return@mapNotNull null
                val encodedPath = URLEncoder.encode(path, "UTF-8")
                val downloadUrl = "$baseUrl/webapi/entry.cgi?api=SYNO.FileStation.Download" +
                        "&version=2&method=download&path=$encodedPath&mode=download&_sid=$sid"

                ImageItem(url = downloadUrl, name = name)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "List images error", e)
            emptyList()
        }
    }

    private fun buildTrustAllClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "SynologySource"
    }
}
