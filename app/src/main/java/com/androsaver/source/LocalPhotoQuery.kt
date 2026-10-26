package com.androsaver.source

import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Only a completed provider query can authoritatively report an empty library. */
internal suspend fun <T> localPhotoQuery(query: suspend () -> List<T>?): List<T> = try {
    query() ?: throw IOException("Device photo provider returned no cursor")
} catch (error: CancellationException) {
    throw error
} catch (error: SecurityException) {
    throw ImageSourcePermissionException("Device photo permission is unavailable")
} catch (error: IOException) {
    throw error
} catch (error: Exception) {
    throw IOException("Device photo provider query failed", error)
}
