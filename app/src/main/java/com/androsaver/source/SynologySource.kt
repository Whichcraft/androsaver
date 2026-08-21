package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.awaitResponse
import com.androsaver.stringLimited
import com.androsaver.HttpClients
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.FormBody
import java.net.URLEncoder

class SynologySource(private val context: Context) : ImageSource {

    override val name = "Synology NAS"

    private val gson = Gson()

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

    internal data class ConnectionConfig(
        val host: String,
        val port: String,
        val username: String,
        val password: String,
        val useHttps: Boolean,
        val allowInsecure: Boolean
    )

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.SYNOLOGY_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.SYNOLOGY_USERNAME, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.SYNOLOGY_PASSWORD, null).isNullOrEmpty()
    }

    internal suspend fun probeConnection(config: ConnectionConfig): Boolean = withContext(Dispatchers.IO) {
        val host = config.host
        val port = config.port
        val user = config.username
        val password = config.password
        val https = config.useHttps
        val insecure = config.allowInsecure
        val base = "${if (https) "https" else "http"}://$host:$port".toHttpUrlOrNull()
            ?.takeIf { it.encodedPath == "/" && it.query == null }?.toString()?.removeSuffix("/")
            ?: throw java.io.IOException("Invalid Synology host or port")
        loginWithFallback(host, port, base, user, password, insecure).sid.isNotBlank()
    }

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        val prefs = com.androsaver.Prefs.get(context)
        val host = prefs.getString(Prefs.SYNOLOGY_HOST, null) ?: return@withContext emptyList()
        val port = prefs.getString(Prefs.SYNOLOGY_PORT, "5000") ?: "5000"
        val useHttps = prefs.getBoolean(Prefs.SYNOLOGY_USE_HTTPS, true)
        val allowInsecure = prefs.getBoolean(Prefs.SYNOLOGY_ALLOW_INSECURE, false)
        val username = prefs.getString(Prefs.SYNOLOGY_USERNAME, null) ?: return@withContext emptyList()
        val password = prefs.getString(Prefs.SYNOLOGY_PASSWORD, null) ?: return@withContext emptyList()
        val folder = prefs.getString(Prefs.SYNOLOGY_FOLDER, "/photos")?.ifEmpty { "/photos" } ?: "/photos"

        val scheme = if (useHttps) "https" else "http"
        val baseUrl = "$scheme://$host:$port".toHttpUrlOrNull()
            ?.takeIf { it.encodedPath == "/" && it.query == null }
            ?.toString()?.removeSuffix("/")
            ?: throw java.io.IOException("Invalid Synology host or port")
        val session = loginWithFallback(host, port, baseUrl, username, password, allowInsecure)
        val client = session.client
        val sid = session.sid
        // Don't logout here — the SID is embedded in image URLs, and Glide loads images
        // after getImageUrls() returns.  Let the Synology session expire naturally (~30 min).
        listImages(client, session.baseUrl, folder, sid, if (session.insecure) session.baseUrl else null)
    }

    private data class LoginSession(
        val client: okhttp3.OkHttpClient,
        val baseUrl: String,
        val sid: String,
        val insecure: Boolean
    )

    /**
     * Synology installations commonly expose HTTP on 5000 and HTTPS on 5001,
     * with certificates that are self-signed, expired, or absent. Try the
     * configured endpoint first, then use the explicitly self-hosted trust-all
     * transport for TLS certificate failures, and finally try the conventional
     * HTTP port when HTTPS was selected against a NAS without TLS.
     */
    private suspend fun loginWithFallback(
        host: String,
        port: String,
        configuredBase: String,
        username: String,
        password: String,
        explicitlyInsecure: Boolean
    ): LoginSession {
        val configuredClient = HttpClients.forHost(context, host, explicitlyInsecure)
        try {
            return LoginSession(
                configuredClient,
                configuredBase,
                login(configuredClient, configuredBase, username, password),
                explicitlyInsecure
            )
        } catch (first: Throwable) {
            if (first is kotlinx.coroutines.CancellationException) throw first
            if (!isTlsFailure(first) || configuredBase.startsWith("http://")) throw first

            val trustClient = HttpClients.trustAll
            try {
                return LoginSession(
                    trustClient,
                    configuredBase,
                    login(trustClient, configuredBase, username, password),
                    true
                )
            } catch (tlsFailure: Throwable) {
                if (tlsFailure is kotlinx.coroutines.CancellationException) throw tlsFailure
                if (!isTlsFailure(tlsFailure)) throw tlsFailure
                val httpPort = if (port == "5001") "5000" else port
                val httpBase = "http://$host:$httpPort"
                val httpClient = HttpClients.standard
                return LoginSession(
                    httpClient,
                    httpBase,
                    login(httpClient, httpBase, username, password),
                    false
                )
            }
        }
    }

    private fun isTlsFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        repeat(8) {
            if (current is javax.net.ssl.SSLException || current is java.security.cert.CertificateException) return true
            current = current?.cause
        }
        val message = throwable.message?.lowercase().orEmpty()
        return listOf("ssl", "tls", "certificate", "handshake", "hostname").any(message::contains)
    }

    private suspend fun login(client: okhttp3.OkHttpClient, baseUrl: String, username: String, password: String): String {
        val url = "$baseUrl/webapi/auth.cgi"
        val body = FormBody.Builder()
            .add("api", "SYNO.API.Auth")
            .add("version", "3")
            .add("method", "login")
            .add("account", username)
            .add("passwd", password)
            .add("session", "AndroSaver")
            .add("format", "sid")
            .build()

        val response = client.newCall(Request.Builder().url(url).post(body).build()).awaitResponse()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw java.io.IOException("HTTP error code $code")
        }
        val json = response.use { gson.fromJson(it.body?.stringLimited(MAX_RESPONSE_BYTES), JsonObject::class.java) }
        if (json.get("success")?.asBoolean == true) {
            return json.getAsJsonObject("data")?.get("sid")?.asString
                ?: throw java.io.IOException("Invalid API response: missing SID")
        } else {
            val error = json.get("error")?.toString() ?: "Unknown error"
            throw java.io.IOException("Login failed: $error")
        }
    }

    private suspend fun listImages(client: okhttp3.OkHttpClient, baseUrl: String, folder: String, sid: String, insecureEndpoint: String?): List<ImageItem> {
        val encodedFolder = URLEncoder.encode(folder, "UTF-8")
        val result = mutableListOf<ImageItem>()
        val pageSize = 200
        var offset = 0
        var total = Int.MAX_VALUE
        var scanned = 0
        var pages = 0
        while (result.size < 2000 && offset < total && scanned < MAX_SCANNED_ENTRIES && pages < MAX_PAGES) {
            val url = "$baseUrl/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list" +
                    "&folder_path=$encodedFolder&filetype=file&offset=$offset&limit=$pageSize&_sid=$sid"
            val response = client.newCall(Request.Builder().url(url).build()).awaitResponse()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw java.io.IOException("HTTP error code $code")
            }
            val json = response.use { gson.fromJson(it.body?.stringLimited(MAX_RESPONSE_BYTES), JsonObject::class.java) }
            if (json.get("success")?.asBoolean != true) {
                val error = json.get("error")?.toString() ?: "Unknown error"
                throw java.io.IOException("List files failed: $error")
            }
            val data = json.getAsJsonObject("data")
                ?: throw java.io.IOException("Invalid API response: missing data")
            val files = data.getAsJsonArray("files")
                ?: throw java.io.IOException("Invalid API response: missing files array")
            total = data.get("total")?.asInt ?: (offset + files.size())
            scanned += files.size()
            pages++
            if (files.isEmpty()) break
            val before = result.size
            result += files.mapNotNull { file ->
            val obj = file.asJsonObject
            val name = obj.get("name")?.asString ?: return@mapNotNull null
            if (name.substringAfterLast('.', "").lowercase() !in imageExtensions) return@mapNotNull null

            val path = obj.get("path")?.asString ?: return@mapNotNull null
            val encodedPath = URLEncoder.encode(path, "UTF-8")
            val downloadUrl = "$baseUrl/webapi/entry.cgi?api=SYNO.FileStation.Download" +
                    "&version=2&method=download&path=$encodedPath&mode=download&_sid=$sid"

            ImageItem(url = downloadUrl, name = name, stableId = "synology:$path", insecureEndpoint = insecureEndpoint)
            }.take(2000 - result.size)
            offset += files.size()
            if (result.size == before && files.size() < pageSize) break
        }
        return result
    }

    companion object {
        private const val TAG = "SynologySource"
        private const val MAX_PAGES = 100
        private const val MAX_SCANNED_ENTRIES = 20_000
        private const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    }
}
