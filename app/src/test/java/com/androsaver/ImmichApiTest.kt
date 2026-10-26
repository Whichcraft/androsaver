package com.androsaver

import com.androsaver.source.*
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ImmichApiTest {
    @Test fun probesAndListingsUseAuthenticatedPostSearchInsteadOfTheRemovedGetAssetsRoute() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = HttpClients.selfHosted.newBuilder().dispatcher(Dispatcher()).connectionPool(ConnectionPool()).addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"assets":{"items":[],"nextPage":null}}""".toResponseBody()).build()
        }.build()
        try {
            val payload = JsonObject().apply { addProperty("page", 1); addProperty("size", 1) }
            ImmichApi(client, "https://photos.invalid", "nonfunctional-placeholder").search(payload)
            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/search/metadata", request.url.encodedPath)
            assertEquals("nonfunctional-placeholder", request.header("x-api-key"))
            assertEquals(payload.toString(), Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
