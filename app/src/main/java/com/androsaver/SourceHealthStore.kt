package com.androsaver

import android.content.Context
import com.androsaver.source.ImageSourceResult
import com.androsaver.source.ImageSourceRegistry
import kotlinx.coroutines.*
import java.util.Date

/** Persistent typed metadata only; no request URLs, exception messages or credentials. */
internal object SourceHealthStore {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("source_health", Context.MODE_PRIVATE)
    fun record(context: Context, name: String, result: ImageSourceResult, health: SourceSnapshots.Health? = null) {
        val prefs = prefs(context)
        val at = System.currentTimeMillis()
        val success = result !is ImageSourceResult.Failure
        val status = when (result) {
            is ImageSourceResult.Failure -> result.kind.name
            is ImageSourceResult.Success -> if (result.retryableFailures) "PARTIAL" else "OK"
            else -> "EMPTY"
        }
        prefs.edit().putString("$name.status", status).putLong("$name.attempt", at)
            .putString("$name.details", (result as? ImageSourceResult.Failure)?.let {
                (it.exceptionTypes + listOfNotNull(it.httpStatus?.let { code -> "HTTP $code" })).joinToString(" → ")
            }.orEmpty()).apply {
                if (success) putLong("$name.success", health?.lastSuccessAt ?: at)
            }.apply()
    }
    fun schedule(context: Context, at: Long?) { prefs(context).edit().putLong("next", at ?: 0L).apply() }
    fun report(context: Context): String {
        val prefs = prefs(context)
        val names = (ImageSourceRegistry.configured(context).map { it.name } + prefs.all.keys
            .filter { it.endsWith(".status") }.map { it.removeSuffix(".status") }).distinct().sorted()
        return names.joinToString("\n\n") { name ->
            buildString {
                append(name).append(": ").append(prefs.getString("$name.status", null) ?: context.getString(R.string.source_not_checked))
                val success = prefs.getLong("$name.success", 0L)
                val attempt = prefs.getLong("$name.attempt", 0L)
                if (success > 0) append("\n").append(context.getString(R.string.source_last_success, Date(success).toString()))
                if (attempt > 0) append("\n").append(context.getString(R.string.source_last_attempt, Date(attempt).toString()))
                prefs.getString("$name.details", null)?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
            }
        } + "\n\n" + prefs.getLong("next", 0L).let {
            if (it > System.currentTimeMillis()) context.getString(R.string.source_next_check, Date(it).toString())
            else context.getString(R.string.source_no_active_check)
        }
    }
    suspend fun checkNow(context: Context) = coroutineScope {
        val sources = ImageSourceRegistry.configured(context)
        val results = sources.map { source -> async {
            val result = try { withTimeout(60_000L) { source.enumerate() } }
            catch (_: TimeoutCancellationException) { ImageSourceResult.Failure(ImageSourceResult.FailureKind.TIMEOUT) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { com.androsaver.source.sourceFailure(error) }
            record(context, source.name, result)
            source to result
        } }.awaitAll()
        SourceRefreshSignals.request()
        val remote = results.filter { it.first.cacheScope != null }
        ImageCache(context).warm(remote.map { (it.second as? ImageSourceResult.Success)?.items.orEmpty() }) {
            com.androsaver.source.SourceIdentity.eligible(Prefs.get(context), it.sourceScope)
        }
    }
}
