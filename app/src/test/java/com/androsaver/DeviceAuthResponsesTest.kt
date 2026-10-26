package com.androsaver

import com.androsaver.auth.DeviceAuthResponses
import com.google.gson.JsonParser
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DeviceAuthResponsesTest {
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject
    @Test fun rejectsOversizedBodiesAndInvalidCredentialsBeforePublication() {
        assertTrue(runCatching { "x".repeat(65_537).toResponseBody().stringLimited(65_536) }.isFailure)
        for (body in listOf("{}", """{"access_token":"","refresh_token":"placeholder"}""", """{"access_token":true,"refresh_token":"placeholder"}""")) {
            assertTrue(runCatching { DeviceAuthResponses.success(json(body)) }.isFailure)
        }
        assertEquals("placeholder", DeviceAuthResponses.success(json("""{"access_token":"placeholder","refresh_token":"placeholder"}""")).accessToken)
    }
    @Test fun validatesCodesExpiryAndVerificationTransport() {
        val valid = json("""{"device_code":"placeholder","user_code":"placeholder","verification_url":"https://example.invalid/device","expires_in":60}""")
        assertEquals(60, DeviceAuthResponses.device(valid, true).expiresIn)
        valid.addProperty("expires_in", -1)
        assertTrue(runCatching { DeviceAuthResponses.device(valid, true) }.isFailure)
        valid.addProperty("expires_in", 60)
        valid.addProperty("verification_url", "http://example.invalid")
        assertTrue(runCatching { DeviceAuthResponses.device(valid, true) }.isFailure)
    }
}
