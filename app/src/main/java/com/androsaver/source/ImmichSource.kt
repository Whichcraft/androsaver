package com.androsaver.source

import android.content.Context
import com.androsaver.HttpClients
import com.androsaver.Prefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Fetches images from a self-hosted Immich instance via its REST API. */
class ImmichSource(private val context: Context) : ImageSource {

    override val name = "Immich"

    internal data class ConnectionConfig(
        val host: String,
        val port: String,
        val apiKey: String,
        val useHttps: Boolean,
        val allowInsecure: Boolean
    )

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.IMMICH_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.IMMICH_API_KEY, null).isNullOrEmpty()
    }

    internal suspend fun probeConnection(config: ConnectionConfig): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val host = config.host
        val port = config.port
        val key = config.apiKey
        val https = config.useHttps
        val insecure = config.allowInsecure
        if (!https && !insecure) throw java.io.IOException("HTTP is disabled")
        val base = "${if (https) "https" else "http"}://$host:$port".toHttpUrlOrNull()
            ?.takeIf { it.encodedPath == "/" && it.query == null }?.toString()?.removeSuffix("/")
            ?: throw java.io.IOException("Invalid Immich host or port")
        val response = ImmichApi(HttpClients.forHost(context, host, insecure), base, key).search(
            com.google.gson.JsonObject().apply {
                addProperty("size", 1); addProperty("page", 1); addProperty("type", "IMAGE")
            })
        response.getAsJsonObject("assets")?.getAsJsonArray("items")
            ?: throw com.google.gson.JsonParseException("Missing Immich metadata-search assets")
        true
    }

    override suspend fun getImageUrls(): List<ImageItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs    = com.androsaver.Prefs.get(context)
            val host     = prefs.getString(Prefs.IMMICH_HOST, null) ?: return@withContext emptyList()
            val port     = prefs.getString(Prefs.IMMICH_PORT, "2283") ?: "2283"
            val useHttps = prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, true)
            val allowInsecure = prefs.getBoolean(Prefs.IMMICH_ALLOW_INSECURE, false)
            val apiKey   = prefs.getString(Prefs.IMMICH_API_KEY, null) ?: return@withContext emptyList()
            val albumId  = prefs.getString(Prefs.IMMICH_ALBUM_ID, "")?.trim() ?: ""

            val scheme  = if (useHttps) "https" else "http"
            if (!useHttps && !allowInsecure) throw java.io.IOException("HTTP is disabled; enable insecure connections explicitly")
            val baseUrl = "$scheme://$host:$port".toHttpUrlOrNull()
                ?.takeIf { it.encodedPath == "/" && it.query == null }
                ?.toString()?.removeSuffix("/")
                ?: throw java.io.IOException("Invalid Immich host or port")
            val client = HttpClients.forHost(context, host, allowInsecure)

            val api = ImmichApi(client, baseUrl, apiKey)
            ImmichPages.list(albumId.takeIf { it.isNotBlank() }, api::search).map { asset ->
                val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
                    .addPathSegments("api/assets").addPathSegment(asset.id)
                    .addPathSegment("thumbnail").addQueryParameter("size", "preview").build()
                ImageItem(url.toString(), asset.name, headers = mapOf("x-api-key" to apiKey),
                    stableId = "immich:${asset.id}", contentVersion = asset.version,
                    insecureEndpoint = if (allowInsecure) baseUrl else null, credentialEndpoint = baseUrl)
            }
        }

}
