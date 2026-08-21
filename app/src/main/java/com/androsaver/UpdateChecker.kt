package com.androsaver

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long
)

object UpdateChecker {

    private fun manifestUrl(): String {
        val channel = if (BuildConfig.IS_DEV) Prefs.UPDATE_CHANNEL_DEV
                      else Prefs.UPDATE_CHANNEL_STABLE
        return "https://github.com/Whichcraft/androsaver/releases/download/$channel/version.json"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(manifestUrl()).build()
            val body = HttpClients.standard.newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            val json = Gson().fromJson(body, com.google.gson.JsonObject::class.java)
            val versionCode = json.get("versionCode")?.takeIf { it.isJsonPrimitive }?.asInt ?: return@withContext null
            val versionName = json.get("versionName")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val apkUrl = json.get("apkUrl")?.takeIf { it.isJsonPrimitive }?.asString ?: return@withContext null
            val sha256 = json.get("sha256")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase()
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return@withContext null
            val sizeBytes = json.get("sizeBytes")?.takeIf { it.isJsonPrimitive }?.asLong
                ?.takeIf { it in 1..100_000_000 } ?: return@withContext null
            val expectedChannel = if (BuildConfig.IS_DEV) Prefs.UPDATE_CHANNEL_DEV else Prefs.UPDATE_CHANNEL_STABLE
            val parsed = apkUrl.toHttpUrlOrNull() ?: return@withContext null
            val expectedPath = "/Whichcraft/androsaver/releases/download/$expectedChannel/androsaver.apk"
            if (parsed.scheme != "https" || parsed.host != "github.com" || parsed.encodedPath != expectedPath) return@withContext null
            if (versionCode > BuildConfig.VERSION_CODE) UpdateInfo(versionCode, versionName, apkUrl, sha256, sizeBytes) else null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (BuildConfig.DEBUG_LOGGING) android.util.Log.w("UpdateChecker", "Check failed", e)
            null
        }
    }
}
