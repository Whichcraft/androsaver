package com.androsaver.source

import kotlinx.coroutines.CancellationException

class ImageSourceHttpException(val code: Int) : java.io.IOException("Image source HTTP $code")
class ImageSourceAuthenticationException(message: String) : java.io.IOException(message)
class ImageSourcePermissionException(message: String) : java.io.IOException(message)

sealed class ImageSourceResult {
    data class Success(val items: List<ImageItem>) : ImageSourceResult()
    object Empty : ImageSourceResult()
    data class Failure(val kind: FailureKind) : ImageSourceResult()

    enum class FailureKind { NETWORK, AUTHENTICATION, PERMISSION, PARSE, TIMEOUT, UNKNOWN }
}

data class ImageItem(
    val url: String,
    val name: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** Stable provider identity; never contains a temporary fetch URL or secret. */
    val stableId: String = url,
    /** Exact configured self-hosted endpoint allowed to use the insecure client. */
    val insecureEndpoint: String? = null,
    /** Non-secret provider revision or modification time, separate from identity. */
    val contentVersion: String? = null
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
    } catch (e: ImageSourcePermissionException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.PERMISSION)
    } catch (e: ImageSourceHttpException) {
        val kind = when {
            e.code == 401 || e.code == 403 -> ImageSourceResult.FailureKind.AUTHENTICATION
            e.code == 408 || e.code == 425 || e.code == 429 || e.code in 500..599 -> ImageSourceResult.FailureKind.NETWORK
            else -> ImageSourceResult.FailureKind.UNKNOWN
        }
        ImageSourceResult.Failure(kind)
    } catch (e: javax.net.ssl.SSLException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.UNKNOWN)
    } catch (e: java.io.IOException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.NETWORK)
    } catch (e: com.google.gson.JsonParseException) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.PARSE)
    } catch (_: Exception) {
        ImageSourceResult.Failure(ImageSourceResult.FailureKind.UNKNOWN)
    }
}
