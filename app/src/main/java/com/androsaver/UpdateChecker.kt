package com.androsaver

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateChecker {

    private fun manifestUrl(): String {
        val channel = if (BuildConfig.FLAVOR == "dev") Prefs.UPDATE_CHANNEL_DEV
                      else Prefs.UPDATE_CHANNEL_STABLE
        return "https://github.com/Whichcraft/androsaver/releases/download/$channel/version.json"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(manifestUrl()).build()
            val body = HttpClients.standard.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            val info = Gson().fromJson(body, UpdateInfo::class.java)
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) android.util.Log.w("UpdateChecker", "Check failed", e)
            null
        }
    }
}
