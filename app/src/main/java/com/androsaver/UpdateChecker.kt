package com.androsaver

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun manifestUrl(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultChannel = if (BuildConfig.FLAVOR == "dev") Prefs.UPDATE_CHANNEL_DEV
                             else Prefs.UPDATE_CHANNEL_STABLE
        val channel = prefs.getString(Prefs.UPDATE_CHANNEL, defaultChannel) ?: defaultChannel
        return "https://github.com/Whichcraft/androsaver/releases/download/$channel/version.json"
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(manifestUrl(context)).build()
            val body = client.newCall(request).execute().use { response ->
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
