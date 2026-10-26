package com.androsaver

import com.androsaver.source.ImageItem
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AuthenticatedRedirectTest {
    @Test fun validatedTlsAlsoConfinesApiKeysToTheirOrigin() {
        for (target in listOf("https://other.invalid/image", "http://photos.invalid/image", "https://photos.invalid:444/image")) {
            val seen = mutableListOf<Request>()
            val client = HttpClients.selfHosted.newBuilder().dispatcher(Dispatcher()).connectionPool(ConnectionPool()).addInterceptor { chain ->
                seen += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(307)
                    .message("redirect").header("Location", target).body("".toResponseBody()).build()
            }.build()
            try {
                assertThrows(IOException::class.java) {
                    client.newCall(Request.Builder().url("https://photos.invalid/start")
                        .header("x-api-key", "nonfunctional-placeholder").build()).execute().close()
                }
                assertEquals(1, seen.size)
                assertEquals("photos.invalid", seen.single().url.host)
            } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }
    @Test fun imageDownloadsRequireOriginMetadataToMatchBeforeUsingCredentials() {
        val origin = "https://photos.invalid"
        assertSame(HttpClients.selfHosted, HttpClients.imageClient("$origin/image".toHttpUrl(), origin, null))
        assertSame(HttpClients.trustAll, HttpClients.imageClient("$origin/image".toHttpUrl(), origin, origin))
        assertThrows(IOException::class.java) {
            HttpClients.imageClient("https://other.invalid/image".toHttpUrl(), origin, null)
        }
        assertFalse(HttpClients.selfHosted.followRedirects)
        assertFalse(HttpClients.selfHosted.followSslRedirects)
    }
}
