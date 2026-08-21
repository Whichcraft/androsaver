package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.awaitResponse
import com.androsaver.HttpClients
import com.androsaver.Prefs
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.URLDecoder
import java.net.URLEncoder

/** Fetches images from a Nextcloud instance via WebDAV (PROPFIND). */
class NextcloudSource(private val context: Context) : ImageSource {

    override val name = "Nextcloud"

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.NEXTCLOUD_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.NEXTCLOUD_USERNAME, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.NEXTCLOUD_PASSWORD, null).isNullOrEmpty()
    }

    suspend fun probeConnection(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val prefs = Prefs.get(context)
        val host = prefs.getString(Prefs.NEXTCLOUD_HOST, null) ?: return@withContext false
        val port = prefs.getString(Prefs.NEXTCLOUD_PORT, "443") ?: "443"
        val user = prefs.getString(Prefs.NEXTCLOUD_USERNAME, null) ?: return@withContext false
        val password = prefs.getString(Prefs.NEXTCLOUD_PASSWORD, null) ?: return@withContext false
        val folder = prefs.getString(Prefs.NEXTCLOUD_FOLDER, "/Photos")?.ifEmpty { "/Photos" } ?: "/Photos"
        val https = prefs.getBoolean(Prefs.NEXTCLOUD_USE_HTTPS, true)
        val insecure = prefs.getBoolean(Prefs.NEXTCLOUD_ALLOW_INSECURE, false)
        if (!https && !insecure) throw java.io.IOException("HTTP is disabled")
        val base = "${if (https) "https" else "http"}://$host:$port".toHttpUrlOrNull()
            ?.takeIf { it.encodedPath == "/" && it.query == null }?.toString()?.removeSuffix("/")
            ?: throw java.io.IOException("Invalid Nextcloud host or port")
        val davFolder = if (folder.startsWith("/")) folder else "/$folder"
        val davUrl = "$base/remote.php/dav/files/${URLEncoder.encode(user, "UTF-8")}$davFolder"
        val body = """<?xml version="1.0" encoding="UTF-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody("application/xml".toMediaType())
        HttpClients.forHost(context, host, insecure).newCall(
            Request.Builder().url(davUrl).header("Authorization", Credentials.basic(user, password))
                .header("Depth", "0").method("PROPFIND", body).build()
        ).awaitResponse().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP error code ${response.code}")
            response.body?.string()?.isNotBlank() ?: throw java.io.IOException("Empty WebDAV response")
        }
        true
    }

    override suspend fun getImageUrls(): List<ImageItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = com.androsaver.Prefs.get(context)
            val host     = prefs.getString(Prefs.NEXTCLOUD_HOST, null) ?: return@withContext emptyList()
            val port     = prefs.getString(Prefs.NEXTCLOUD_PORT, "443") ?: "443"
            val useHttps = prefs.getBoolean(Prefs.NEXTCLOUD_USE_HTTPS, true)
            val allowInsecure = prefs.getBoolean(Prefs.NEXTCLOUD_ALLOW_INSECURE, false)
            val username = prefs.getString(Prefs.NEXTCLOUD_USERNAME, null) ?: return@withContext emptyList()
            val password = prefs.getString(Prefs.NEXTCLOUD_PASSWORD, null) ?: return@withContext emptyList()
            val folder   = prefs.getString(Prefs.NEXTCLOUD_FOLDER, "/Photos")?.ifEmpty { "/Photos" } ?: "/Photos"

            val scheme     = if (useHttps) "https" else "http"
            if (!useHttps && !allowInsecure) throw java.io.IOException("HTTP is disabled; enable insecure connections explicitly")
            val baseUrl    = "$scheme://$host:$port".toHttpUrlOrNull()
                ?.takeIf { it.encodedPath == "/" && it.query == null }
                ?.toString()?.removeSuffix("/")
                ?: throw java.io.IOException("Invalid Nextcloud host or port")
            val encodedUser = URLEncoder.encode(username, "UTF-8")
            val davFolder  = if (folder.startsWith("/")) folder else "/$folder"
            val davUrl     = "$baseUrl/remote.php/dav/files/$encodedUser$davFolder"
            val credential = Credentials.basic(username, password)
            val client = HttpClients.forHost(context, host, allowInsecure)

            listImages(client, davUrl, credential, baseUrl)
        }

    private suspend fun listImages(client: okhttp3.OkHttpClient, davUrl: String, credential: String, baseUrl: String): List<ImageItem> {
        val body = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:getcontenttype/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>""".toRequestBody("application/xml".toMediaType())

        val request = Request.Builder()
            .url(davUrl)
            .header("Authorization", credential)
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()
        val response = client.newCall(request).awaitResponse()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw java.io.IOException("HTTP error code $code")
        }
        val xml = response.use { it.body?.string() } ?: throw java.io.IOException("Empty response body")
        return parseResponse(xml, baseUrl, credential)
    }

    private fun parseResponse(xml: String, baseUrl: String, credential: String): List<ImageItem> {
        val items = mutableListOf<ImageItem>()
        try {
            val parser = android.util.Xml.newPullParser()
            parser.setInput(xml.reader())

            var href: String? = null
            var isCollection = false
            var contentType: String? = null
            var inHref = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.substringAfterLast(':')
                when (event) {
                    XmlPullParser.START_TAG -> when (tag) {
                        "response"       -> { href = null; isCollection = false; contentType = null }
                        "href"           -> inHref = true
                        "collection"     -> isCollection = true
                        "getcontenttype" -> contentType = parser.nextText()
                    }
                    XmlPullParser.TEXT -> if (inHref) { href = parser.text; inHref = false }
                    XmlPullParser.END_TAG -> {
                        if (tag == "href") inHref = false
                        if (tag == "response" && !isCollection && href != null) {
                            val name = URLDecoder.decode(href.substringAfterLast("/"), "UTF-8")
                            val ext  = name.substringAfterLast('.', "").lowercase()
                            val isImage = contentType?.startsWith("image/") == true || ext in imageExtensions
                            if (isImage && name.isNotEmpty()) {
                                val url = if (href.startsWith("http")) href else "$baseUrl$href"
                                items.add(ImageItem(url = url, name = name,
                                    headers = mapOf("Authorization" to credential),
                                    stableId = "nextcloud:$href"))
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "WebDAV parse error", e)
            throw java.io.IOException("Invalid WebDAV response", e)
        }
        return items
    }

    companion object {
        private const val TAG = "NextcloudSource"
    }
}
