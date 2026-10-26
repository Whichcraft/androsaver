package com.androsaver.auth

import android.content.Context
import com.androsaver.HttpClients
import com.androsaver.awaitResponse
import com.androsaver.stringLimited
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
    private var deviceAccount: AccountTransactions.Ticket? = null

    suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        val prefs = com.androsaver.Prefs.get(context)
        deviceAccount = OAuthAccounts.capture(prefs, "onedrive")
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
                gson.fromJson(response.body?.stringLimited(64 * 1024L), JsonObject::class.java)
            }

            if (json.has("error")) return@withContext null
            DeviceAuthResponses.device(json, google = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun pollForToken(deviceCode: String): AuthResult = withContext(Dispatchers.IO) {
        val prefs = com.androsaver.Prefs.get(context)
        val account = deviceAccount
            ?: return@withContext AuthResult.Error("Start sign-in again")
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
            ).awaitResponse().use { gson.fromJson(it.body?.stringLimited(64 * 1024L), JsonObject::class.java) }

            when {
                json.has("access_token") -> {
                    val credentials = DeviceAuthResponses.success(json)
                    val accessToken = credentials.accessToken
                    val refreshToken = credentials.refreshToken
                    if (!OAuthAccounts.publish(prefs, account) {
                        prefs.edit()
                            .putString(Prefs.ONEDRIVE_ACCESS_TOKEN, accessToken)
                            .putString(Prefs.ONEDRIVE_REFRESH_TOKEN, refreshToken)
                            .putString(com.androsaver.source.SourceIdentity.key("onedrive"), java.util.UUID.randomUUID().toString())
                            .apply()
                    }) return@withContext AuthResult.Error("Account changed; please sign in again")
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
            AuthResult.Error("Authorization request failed")
        }
    }

    fun isAuthorized(): Boolean =
        !com.androsaver.Prefs.get(context)
            .getString(Prefs.ONEDRIVE_REFRESH_TOKEN, null).isNullOrEmpty()

    fun clearAuth() {
        OAuthAccounts.invalidate("onedrive") {
            com.androsaver.Prefs.get(context).edit()
                .remove(Prefs.ONEDRIVE_ACCESS_TOKEN)
                .remove(Prefs.ONEDRIVE_REFRESH_TOKEN)
                .remove(com.androsaver.source.SourceIdentity.key("onedrive"))
                .apply()
        }
    }
}
