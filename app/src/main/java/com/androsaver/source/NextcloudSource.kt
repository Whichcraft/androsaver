package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.HttpClients
import com.androsaver.Prefs
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.net.URLDecoder
import java.net.URLEncoder

/** Fetches images from a Nextcloud instance via WebDAV (PROPFIND). */
class NextcloudSource(private val context: Context) : ImageSource {

    override val name = "Nextcloud"

    private val client = HttpClients.trustAll

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

    override fun isConfigured(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getString(Prefs.NEXTCLOUD_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.NEXTCLOUD_USERNAME, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val host     = prefs.getString(Prefs.NEXTCLOUD_HOST, null) ?: return@withContext emptyList()
            val port     = prefs.getString(Prefs.NEXTCLOUD_PORT, "443") ?: "443"
            val useHttps = prefs.getBoolean(Prefs.NEXTCLOUD_USE_HTTPS, true)
            val username = prefs.getString(Prefs.NEXTCLOUD_USERNAME, null) ?: return@withContext emptyList()
            val password = prefs.getString(Prefs.NEXTCLOUD_PASSWORD, null) ?: return@withContext emptyList()
            val folder   = prefs.getString(Prefs.NEXTCLOUD_FOLDER, "/Photos")?.ifEmpty { "/Photos" } ?: "/Photos"

            val scheme     = if (useHttps) "https" else "http"
            val baseUrl    = "$scheme://$host:$port"
            val encodedUser = URLEncoder.encode(username, "UTF-8")
            val davFolder  = if (folder.startsWith("/")) folder else "/$folder"
            val davUrl     = "$baseUrl/remote.php/dav/files/$encodedUser$davFolder"
            val credential = Credentials.basic(username, password)

            listImages(davUrl, credential, baseUrl)
        }

    private fun listImages(davUrl: String, credential: String, baseUrl: String): List<ImageItem> {
        val body = """<?xml version="1.0" encoding="UTF-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:getcontenttype/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>""".toRequestBody("application/xml".toMediaType())

        return try {
            val request = Request.Builder()
                .url(davUrl)
                .header("Authorization", credential)
                .header("Depth", "1")
                .method("PROPFIND", body)
                .build()
            val xml = client.newCall(request).execute().use { it.body?.string() } ?: return emptyList()
            parseResponse(xml, baseUrl, credential)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "WebDAV list error", e)
            emptyList()
        }
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
                                    headers = mapOf("Authorization" to credential)))
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "WebDAV parse error", e)
        }
        return items
    }

    companion object {
        private const val TAG = "NextcloudSource"
    }
}
