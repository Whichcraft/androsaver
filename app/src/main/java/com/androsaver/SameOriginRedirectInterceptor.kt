package com.androsaver

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Credential-bearing self-hosted requests and TLS exceptions never leave their origin. */
internal class SameOriginRedirectInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val origin = chain.request().url
        var request = chain.request()
        repeat(21) { attempt ->
            val response = chain.proceed(request)
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            val location = response.header("Location") ?: return response
            val target = request.url.resolve(location)
            if (target == null || target.scheme != origin.scheme ||
                target.host != origin.host || target.port != origin.port ||
                target.username.isNotEmpty() || target.password.isNotEmpty()) {
                response.close()
                throw IOException("Endpoint redirect outside configured origin refused")
            }
            if (attempt == 20) {
                response.close()
                throw IOException("Too many endpoint redirects")
            }
            val builder = request.newBuilder().url(target)
            val switchToGet = request.method != "HEAD" &&
                (response.code == 303 || (response.code in setOf(301, 302) && request.method == "POST"))
            if (switchToGet) {
                builder.method("GET", null)
                    .removeHeader("Content-Length").removeHeader("Content-Type").removeHeader("Transfer-Encoding")
            } else if (request.body?.isOneShot() == true || request.body?.isDuplex() == true) {
                return response
            }
            response.close()
            request = builder.build()
        }
        throw IOException("Too many endpoint redirects")
    }
}
