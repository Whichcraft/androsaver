package com.androsaver.auth

import android.content.Context
import androidx.preference.PreferenceManager
import com.androsaver.HttpClients
import com.androsaver.awaitResponse
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

class OneDriveAuthManager(private val context: Context) {

    private val client = HttpClients.standard
    private val gson = Gson()

    suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        val prefs = com.androsaver.Prefs.get(context)
        val clientId = prefs.getString(Prefs.ONEDRIVE_CLIENT_ID, null) ?: return@withContext null

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "Files.Read offline_access")
            .build()

        try {
            val json = client.newCall(
                Request.Builder()
                    .url("https://login.microsoftonline.com/common/oauth2/v2.0/devicecode")
                    .post(body).build()
            ).awaitResponse().use { response ->
                if (!response.isSuccessful) return@withContext null
                gson.fromJson(response.body?.string(), JsonObject::class.java)
            }

            if (json.has("error")) return@withContext null
            DeviceCodeResponse(
                deviceCode      = json.get("device_code").asString,
                userCode        = json.get("user_code").asString,
                verificationUrl = json.get("verification_uri").asString,
                expiresIn       = json.get("expires_in").asInt,
                interval        = json.get("interval")?.asInt ?: 5
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun pollForToken(deviceCode: String): AuthResult = withContext(Dispatchers.IO) {
        val prefs = com.androsaver.Prefs.get(context)
        val clientId = prefs.getString(Prefs.ONEDRIVE_CLIENT_ID, null)
            ?: return@withContext AuthResult.Error("No client ID configured")

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()

        try {
            val json = client.newCall(
                Request.Builder()
                    .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .post(body).build()
            ).awaitResponse().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            when {
                json.has("access_token") -> {
                    val accessToken  = json.get("access_token").asString
                    val refreshToken = json.get("refresh_token").asString
                    prefs.edit()
                        .putString(Prefs.ONEDRIVE_ACCESS_TOKEN, accessToken)
                        .putString(Prefs.ONEDRIVE_REFRESH_TOKEN, refreshToken)
                        .apply()
                    AuthResult.Success(accessToken, refreshToken)
                }
                json.has("error") -> when (json.get("error").asString) {
                    "authorization_pending" -> AuthResult.Pending
                    "slow_down"             -> AuthResult.SlowDown
                    "expired_token"         -> AuthResult.Expired
                    else -> AuthResult.Error(json.get("error").asString)
                }
                else -> AuthResult.Error("Unexpected response")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    fun isAuthorized(): Boolean =
        !com.androsaver.Prefs.get(context)
            .getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null).isNullOrEmpty()

    fun clearAuth() {
        com.androsaver.Prefs.get(context).edit()
            .remove(Prefs.ONEDRIVE_ACCESS_TOKEN)
            .remove(Prefs.ONEDRIVE_REFRESH_TOKEN)
            .apply()
    }
}
