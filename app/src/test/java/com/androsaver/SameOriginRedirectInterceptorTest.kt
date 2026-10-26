package com.androsaver

import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class SameOriginRedirectInterceptorTest {
    @Test fun insecureClientCannotAutomaticallyFollowRedirects() {
        assertFalse(HttpClients.trustAll.followRedirects)
        assertFalse(HttpClients.trustAll.followSslRedirects)
    }

    @Test fun relativeRedirectsStayOnOriginAndPreserve307Bodies() {
        val request = Request.Builder().url("https://example.invalid/start")
            .post("body".toRequestBody()).build()
        val chain = FakeChain(request) { req ->
            if (req.url.encodedPath == "/start") "/finish" else null
        }
        SameOriginRedirectInterceptor().intercept(chain).close()
        assertEquals(2, chain.seen.size)
        assertEquals("POST", chain.seen.last().method)
        assertSame(request.body, chain.seen.last().body)
    }

    @Test fun hostPortAndSchemeChangesAreRejectedBeforeTheNextRequest() {
        listOf("https://other.invalid/image", "https://example.invalid:444/image",
            "http://example.invalid/image", "https://user:placeholder@example.invalid/image"
        ).forEach { location ->
            val chain = FakeChain(Request.Builder().url("https://example.invalid/start").build()) { location }
            assertThrows(IOException::class.java) { SameOriginRedirectInterceptor().intercept(chain) }
            assertEquals(1, chain.seen.size)
        }
    }

    @Test fun redirectLoopsAreBounded() {
        val chain = FakeChain(Request.Builder().url("https://example.invalid/start").build()) { "/start" }
        assertThrows(IOException::class.java) { SameOriginRedirectInterceptor().intercept(chain) }
        assertEquals(21, chain.seen.size)
    }

    private class FakeChain(
        private val original: Request,
        private val redirect: (Request) -> String?
    ) : Interceptor.Chain {
        val seen = mutableListOf<Request>()
        override fun request() = original
        override fun proceed(request: Request): Response {
            seen += request
            val location = redirect(request)
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (location == null) 200 else 307).message("test")
                .body("".toResponseBody()).apply {
                    location?.let { header("Location", it) }
                }.build()
        }
        override fun connection(): Connection? = null
        override fun call(): Call = error("Not used")
        override fun connectTimeoutMillis() = 10_000
        override fun readTimeoutMillis() = 10_000
        override fun writeTimeoutMillis() = 10_000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
