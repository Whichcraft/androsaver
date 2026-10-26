package com.androsaver.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Validate before codes are displayed or credentials are published. Body readers separately enforce 64 KiB. */
internal object DeviceAuthResponses {
    private fun text(json: JsonObject, key: String): String = json.get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?.takeIf { it.isNotBlank() && it.length <= 16_384 } ?: throw JsonParseException("Invalid authorization field")
    fun device(json: JsonObject, google: Boolean): DeviceCodeResponse {
        val expires = json.get("expires_in")?.asInt?.takeIf { it in 1..86_400 }
            ?: throw JsonParseException("Invalid authorization expiry")
        val interval = (json.get("interval")?.asInt ?: 5).takeIf { it in 1..3600 }
            ?: throw JsonParseException("Invalid authorization interval")
        val verification = text(json, if (google) "verification_url" else "verification_uri")
        val url = verification.toHttpUrlOrNull()?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
            ?: throw JsonParseException("Invalid verification URL")
        return DeviceCodeResponse(text(json, "device_code"), text(json, "user_code"), url.toString(), expires, interval)
    }
    fun success(json: JsonObject) = AuthResult.Success(text(json, "access_token"), text(json, "refresh_token"))
}
