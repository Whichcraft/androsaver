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
import java.nio.charset.StandardCharsets

/** Fetches images from a Nextcloud instance via WebDAV (PROPFIND). */
class NextcloudSource(private val context: Context) : ImageSource {

    override val name = "Nextcloud"

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    internal data class ConnectionConfig(
        val host: String,
        val port: String,
        val username: String,
        val password: String,
        val folder: String,
        val useHttps: Boolean,
        val allowInsecure: Boolean
    )

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.NEXTCLOUD_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.NEXTCLOUD_USERNAME, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.NEXTCLOUD_PASSWORD, null).isNullOrEmpty()
    }

    internal suspend fun probeConnection(config: ConnectionConfig): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val host = config.host
        val port = config.port
        val user = config.username
        val password = config.password
        val folder = config.folder.ifEmpty { "/Photos" }
        val https = config.useHttps
        val insecure = config.allowInsecure
        if (!https && !insecure) throw java.io.IOException("HTTP is disabled")
        val base = "${if (https) "https" else "http"}://$host:$port".toHttpUrlOrNull()
            ?.takeIf { it.encodedPath == "/" && it.query == null }
            ?: throw java.io.IOException("Invalid Nextcloud host or port")
        val davUrl = buildDavUrl(base, user, folder)
        val body = """<?xml version="1.0" encoding="UTF-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody("application/xml".toMediaType())
        HttpClients.forHost(context, host, insecure).newCall(
            Request.Builder().url(davUrl).header("Authorization", Credentials.basic(user, password))
                .header("Depth", "0").method("PROPFIND", body).build()
        ).awaitResponse().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP error code ${response.code}")
            response.body?.readLimited(MAX_RESPONSE_BYTES)?.isNotBlank() ?: throw java.io.IOException("Empty WebDAV response")
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
            val base = baseUrl.toHttpUrlOrNull() ?: throw java.io.IOException("Invalid Nextcloud base URL")
            val davUrl     = buildDavUrl(base, username, folder)
            val credential = Credentials.basic(username, password)
            val client = HttpClients.forHost(context, host, allowInsecure)

            listImages(client, davUrl, credential, base, if (allowInsecure) base.toString() else null)
        }

    private suspend fun listImages(client: okhttp3.OkHttpClient, davUrl: okhttp3.HttpUrl, credential: String, baseUrl: okhttp3.HttpUrl, insecureEndpoint: String?): List<ImageItem> {
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
        val xml = response.use { it.body?.readLimited(MAX_RESPONSE_BYTES) } ?: throw java.io.IOException("Empty response body")
        return parseResponse(xml, baseUrl, credential, insecureEndpoint)
    }

    internal fun parseResponse(xml: String, baseUrl: okhttp3.HttpUrl, credential: String, insecureEndpoint: String? = null): List<ImageItem> {
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
                            val resolved = baseUrl.resolve(href)
                            if (resolved == null || !sameOrigin(baseUrl, resolved)) continue
                            val name = resolved.pathSegments.lastOrNull().orEmpty()
                            val ext  = name.substringAfterLast('.', "").lowercase()
                            val isImage = contentType?.startsWith("image/") == true || ext in imageExtensions
                            if (isImage && name.isNotEmpty() && items.size < 2000) {
                                items.add(ImageItem(url = resolved.toString(), name = name,
                                    headers = mapOf("Authorization" to credential),
                                    stableId = "nextcloud:${resolved.encodedPath}",
                                    insecureEndpoint = insecureEndpoint))
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

    private fun buildDavUrl(base: okhttp3.HttpUrl, username: String, folder: String): okhttp3.HttpUrl {
        val builder = base.newBuilder()
            .addPathSegment("remote.php")
            .addPathSegment("dav")
            .addPathSegment("files")
            .addPathSegment(username)
        folder.trim('/').split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun sameOrigin(base: okhttp3.HttpUrl, candidate: okhttp3.HttpUrl): Boolean =
        base.scheme == candidate.scheme &&
            base.host == candidate.host &&
            base.port == candidate.port

    private fun okhttp3.ResponseBody.readLimited(maxBytes: Long): String {
        byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw java.io.IOException("WebDAV response exceeds size limit")
                output.write(buffer, 0, read)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }

    companion object {
        private const val TAG = "NextcloudSource"
        private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    }
}
