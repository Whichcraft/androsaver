package com.androsaver.source

import com.androsaver.awaitResponse
import com.androsaver.stringLimited
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** One authenticated API contract for connection probes and actual enumeration. */
internal class ImmichApi(private val client: OkHttpClient, private val base: String, private val key: String) {
    suspend fun search(payload: JsonObject): JsonObject {
        val request = Request.Builder().url("$base/api/search/metadata")
            .header("x-api-key", key)
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw ImageSourceHttpException(response.code)
            val raw = response.body?.stringLimited(8L * 1024 * 1024)
                ?: throw java.io.IOException("Empty Immich response")
            val result = JsonParser.parseString(raw)
            if (!result.isJsonObject) throw JsonParseException("Invalid Immich metadata-search result")
            result.asJsonObject
        }
    }
}
