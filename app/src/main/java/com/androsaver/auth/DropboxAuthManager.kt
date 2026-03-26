package com.androsaver.auth

import android.content.Context
import androidx.preference.PreferenceManager
import com.androsaver.HttpClients
import com.androsaver.Prefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Request

class DropboxAuthManager(private val context: Context) {

    private val client = HttpClients.standard
    private val gson = Gson()

    /** Returns the URL the user must visit on another device to authorize. */
    fun buildAuthUrl(): String? {
        val appKey = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(Prefs.DROPBOX_APP_KEY, null) ?: return null
        return "https://www.dropbox.com/oauth2/authorize" +
               "?client_id=$appKey&response_type=code&token_access_type=offline"
    }

    /** Exchanges the authorization code shown by Dropbox for access + refresh tokens. */
    suspend fun exchangeCode(code: String): AuthResult = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val appKey    = prefs.getString(Prefs.DROPBOX_APP_KEY, null)
            ?: return@withContext AuthResult.Error("No App Key configured")
        val appSecret = prefs.getString(Prefs.DROPBOX_APP_SECRET, null)
            ?: return@withContext AuthResult.Error("No App Secret configured")

        val body = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .build()

        try {
            val json = client.newCall(
                Request.Builder()
                    .url("https://api.dropboxapi.com/oauth2/token")
                    .header("Authorization", Credentials.basic(appKey, appSecret))
                    .post(body).build()
            ).execute().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            if (json.has("error")) return@withContext AuthResult.Error(
                json.get("error_description")?.asString ?: json.get("error").asString
            )

            val accessToken  = json.get("access_token").asString
            val refreshToken = json.get("refresh_token").asString
            prefs.edit()
                .putString(Prefs.DROPBOX_ACCESS_TOKEN, accessToken)
                .putString(Prefs.DROPBOX_REFRESH_TOKEN, refreshToken)
                .apply()
            AuthResult.Success(accessToken, refreshToken)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    /** Returns a valid access token, refreshing silently if necessary. */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val appKey       = prefs.getString(Prefs.DROPBOX_APP_KEY, null) ?: return@withContext null
        val appSecret    = prefs.getString(Prefs.DROPBOX_APP_SECRET, null) ?: return@withContext null
        val refreshToken = prefs.getString(Prefs.DROPBOX_REFRESH_TOKEN, null) ?: return@withContext null

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        try {
            val json = client.newCall(
                Request.Builder()
                    .url("https://api.dropboxapi.com/oauth2/token")
                    .header("Authorization", Credentials.basic(appKey, appSecret))
                    .post(body).build()
            ).execute().use { gson.fromJson(it.body?.string(), JsonObject::class.java) }

            if (json.has("error")) return@withContext null
            val newToken = json.get("access_token").asString
            prefs.edit().putString(Prefs.DROPBOX_ACCESS_TOKEN, newToken).apply()
            newToken
        } catch (e: Exception) { null }
    }

    fun isAuthorized(): Boolean =
        !PreferenceManager.getDefaultSharedPreferences(context)
            .getString(Prefs.DROPBOX_REFRESH_TOKEN, null).isNullOrEmpty()

    fun clearAuth() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(Prefs.DROPBOX_ACCESS_TOKEN)
            .remove(Prefs.DROPBOX_REFRESH_TOKEN)
            .apply()
    }
}
