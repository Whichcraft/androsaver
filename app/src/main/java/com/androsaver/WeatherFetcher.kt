package com.androsaver

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WeatherFetcher(context: Context) {
    private val context = context.applicationContext

    companion object {
        private const val TAG = "WeatherFetcher"
        private const val CACHE_MS = 30 * 60 * 1000L
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_JSON = "json"
        private const val KEY_TS   = "ts"
        private const val KEY_CITY = "city"
        private const val OWM_URL  = "https://api.openweathermap.org/data/2.5/weather"
    }

    data class WeatherData(val tempC: Float, val description: String)

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    suspend fun getWeather(city: String, apiKey: String): WeatherData? {
        if (city.isBlank() || apiKey.isBlank()) return null
        val cached = loadCached(city)
        if (cached != null) return cached
        return withContext(Dispatchers.IO) { fetchFromApi(city, apiKey) }
    }

    private fun loadCached(city: String): WeatherData? {
        val cachedCity = prefs.getString(KEY_CITY, null) ?: return null
        if (!cachedCity.equals(city, ignoreCase = true)) return null
        val ts = prefs.getLong(KEY_TS, 0L)
        val age = System.currentTimeMillis() - ts
        if (age !in 0..CACHE_MS) return null
        val json = prefs.getString(KEY_JSON, null) ?: return null
        return parseJson(json)
    }

    private fun saveCached(city: String, rawJson: String) {
        prefs.edit()
            .putString(KEY_JSON, rawJson)
            .putString(KEY_CITY, city)
            .putLong(KEY_TS, System.currentTimeMillis())
            .apply()
    }

    private fun fetchFromApi(city: String, apiKey: String): WeatherData? {
        return try {
            val url = OWM_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", city)
                .addQueryParameter("appid", apiKey)
                .addQueryParameter("units", "metric")
                .build()
            client.newCall(Request.Builder().url(url).build()).awaitResponse().use { resp ->
                if (!resp.isSuccessful) { if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Weather API error ${resp.code}"); return null }
                val body = resp.body?.string() ?: return null
                val data = parseJson(body)
                if (data != null) saveCached(city, body)
                data
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Weather fetch failed: ${e::class.java.simpleName}")
            null
        }
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
