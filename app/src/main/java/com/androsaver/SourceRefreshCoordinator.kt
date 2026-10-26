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
        snapshots.bind(source.name, source.cacheScope)
        val result = try {
            withTimeout(60_000L) { source.enumerate() }
        } catch (e: TimeoutCancellationException) {
            ImageSourceResult.Failure(ImageSourceResult.FailureKind.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            com.androsaver.source.sourceFailure(error)
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
        sources: List<ImageSource>, onUpdated: (freshItemsAvailable: Boolean) -> Unit,
        onItems: suspend (ImageSource, List<ImageItem>) -> Unit
    ) {
        snapshots.retain(sources.map { it.name }.toSet())
        sources.forEach { snapshots.bind(it.name, it.cacheScope) }
        onUpdated(false)
        ProgressiveSources.load(sources, fetch = { source ->
            fetch(source).also { onUpdated(!it.isNullOrEmpty()) }
        }, onItems = onItems)
    }
}
