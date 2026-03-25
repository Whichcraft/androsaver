package com.androsaver

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class WeatherFetcher(private val context: Context) {

    companion object {
        private const val TAG = "WeatherFetcher"
        private const val CACHE_MS = 30 * 60 * 1000L
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_JSON = "json"
        private const val KEY_TS   = "ts"
        private const val OWM_URL  = "https://api.openweathermap.org/data/2.5/weather"
    }

    data class WeatherData(val tempC: Float, val description: String)

    private val gson = Gson()
    private val client = OkHttpClient()
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    suspend fun getWeather(city: String, apiKey: String): WeatherData? {
        if (city.isBlank() || apiKey.isBlank()) return null
        val cached = loadCached()
        if (cached != null) return cached
        return withContext(Dispatchers.IO) { fetchFromApi(city, apiKey) }
    }

    private fun loadCached(): WeatherData? {
        val ts = prefs.getLong(KEY_TS, 0L)
        if (System.currentTimeMillis() - ts > CACHE_MS) return null
        val json = prefs.getString(KEY_JSON, null) ?: return null
        return parseJson(json)
    }

    private fun saveCached(data: WeatherData, rawJson: String) {
        prefs.edit().putString(KEY_JSON, rawJson).putLong(KEY_TS, System.currentTimeMillis()).apply()
    }

    private fun fetchFromApi(city: String, apiKey: String): WeatherData? {
        return try {
            val url = "$OWM_URL?q=${URLEncoder.encode(city, "UTF-8")}&appid=$apiKey&units=metric"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val body = resp.body?.string() ?: return null
            if (!resp.isSuccessful) { Log.w(TAG, "Weather API error ${resp.code}"); return null }
            val data = parseJson(body)
            if (data != null) saveCached(data, body)
            data
        } catch (e: Exception) { Log.w(TAG, "Weather fetch failed: ${e.message}"); null }
    }

    private fun parseJson(json: String): WeatherData? {
        return try {
            val obj  = gson.fromJson(json, JsonObject::class.java)
            val temp = obj.getAsJsonObject("main")?.get("temp")?.asFloat ?: return null
            val desc = obj.getAsJsonArray("weather")?.firstOrNull()?.asJsonObject?.get("description")?.asString ?: ""
            WeatherData(temp, desc.replaceFirstChar { it.uppercase() })
        } catch (_: Exception) { null }
    }
}
