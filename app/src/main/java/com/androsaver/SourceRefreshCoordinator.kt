package com.androsaver

import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSource
import com.androsaver.source.ImageSourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Fetch orchestration and snapshots are independent of Views and slide transitions. */
internal class SourceRefreshCoordinator(
    val snapshots: SourceSnapshots = SourceSnapshots(),
    private val onResult: (ImageSource, ImageSourceResult) -> Unit = { _, _ -> }
) {
    suspend fun fetch(source: ImageSource): List<ImageItem>? {
        val result = try {
            withTimeout(60_000L) { source.enumerate() }
        } catch (e: TimeoutCancellationException) {
            ImageSourceResult.Failure(ImageSourceResult.FailureKind.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ImageSourceResult.Failure(ImageSourceResult.FailureKind.UNKNOWN)
        }
        snapshots.update(source.name, result)
        onResult(source, result)
        return when (result) {
            is ImageSourceResult.Success -> result.items
            ImageSourceResult.Empty -> emptyList()
            is ImageSourceResult.Failure -> null
        }
    }

    suspend fun refresh(
        sources: List<ImageSource>, onUpdated: () -> Unit,
        onItems: suspend (ImageSource, List<ImageItem>) -> Unit
    ) {
        snapshots.retain(sources.map { it.name }.toSet())
        onUpdated()
        ProgressiveSources.load(sources, fetch = { source ->
            fetch(source).also { onUpdated() }
        }, onItems = onItems)
    }
}
