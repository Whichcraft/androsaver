package com.androsaver.source

import kotlinx.coroutines.CancellationException

class ImageSourceAuthenticationException(message: String) : java.io.IOException(message)

sealed class ImageSourceResult {
    data class Success(val items: List<ImageItem>) : ImageSourceResult()
    object Empty : ImageSourceResult()
    data class Failure(val kind: FailureKind) : ImageSourceResult()

    enum class FailureKind { NETWORK, AUTHENTICATION, PARSE, TIMEOUT, UNKNOWN }
}

data class ImageItem(
    val url: String,
    val name: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** Stable provider identity; never contains a temporary fetch URL or secret. */
    val stableId: String = url
)

interface ImageSource {
    val name: String
    suspend fun getImageUrls(): List<ImageItem>
    fun isConfigured(): Boolean

    /** Converts provider exceptions into a non-secret outcome for UI/worker callers. */
    suspend fun enumerate(): ImageSourceResult = try {
        val items = getImageUrls()
        if (items.isEmpty()) ImageSourceResult.Empty else ImageSourceResult.Success(items)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ImageSourceAuthenticationException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.AUTHENTICATION)
    } catch (e: java.io.IOException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.NETWORK)
    } catch (e: com.google.gson.JsonParseException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.PARSE)
    } catch (_: Exception) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.UNKNOWN)
    }
}
