package com.androsaver.auth

import android.content.Context
import androidx.preference.PreferenceManager
import com.androsaver.HttpClients
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int
)

sealed class AuthResult {
    data class Success(val accessToken: String, val refreshToken: String) : AuthResult()
    object Pending : AuthResult()
    object Expired : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class GoogleAuthManager(private val context: Context) {

    private val client = HttpClients.standard
    private val gson = Gson()

    suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val clientId = prefs.getString(Prefs.GOOGLE_CLIENT_ID, null) ?: return@withContext null

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "https://www.googleapis.com/auth/drive.readonly")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/device/code")
            .post(body)
            .build()

        try {
            val json = client.newCall(request).execute().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }
            if (json.has("error")) return@withContext null
            DeviceCodeResponse(
                deviceCode = json.get("device_code").asString,
                userCode = json.get("user_code").asString,
                verificationUrl = json.get("verification_url").asString,
                expiresIn = json.get("expires_in").asInt,
                interval = json.get("interval")?.asInt ?: 5
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pollForToken(deviceCode: String): AuthResult = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val clientId = prefs.getString(Prefs.GOOGLE_CLIENT_ID, null)
            ?: return@withContext AuthResult.Error("No client ID configured")
        val clientSecret = prefs.getString(Prefs.GOOGLE_CLIENT_SECRET, null)
            ?: return@withContext AuthResult.Error("No client secret configured")

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        try {
            val json = client.newCall(request).execute().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            when {
                json.has("access_token") -> {
                    val accessToken = json.get("access_token").asString
                    val refreshToken = json.get("refresh_token").asString
                    prefs.edit()
                        .putString(Prefs.GOOGLE_ACCESS_TOKEN, accessToken)
                        .putString(Prefs.GOOGLE_REFRESH_TOKEN, refreshToken)
                        .apply()
                    AuthResult.Success(accessToken, refreshToken)
                }
                json.has("error") -> when (json.get("error").asString) {
                    "authorization_pending" -> AuthResult.Pending
                    "slow_down" -> AuthResult.Pending
                    "expired_token" -> AuthResult.Expired
                    else -> AuthResult.Error(json.get("error").asString)
                }
                else -> AuthResult.Error("Unexpected response")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    fun isAuthorized(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getString(Prefs.GOOGLE_REFRESH_TOKEN, null).isNullOrEmpty()
    }

    fun clearAuth() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(Prefs.GOOGLE_ACCESS_TOKEN)
            .remove(Prefs.GOOGLE_REFRESH_TOKEN)
            .apply()
    }
}
