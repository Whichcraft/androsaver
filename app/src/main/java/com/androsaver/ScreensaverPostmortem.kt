package com.androsaver

import java.io.File
import java.io.FileOutputStream
import java.util.Date

/** One process-wide handler. Sessions contain metadata, never Activities or Views. */
internal object ScreensaverPostmortem {
    private data class Session(val file: File, val mode: String)
    private val sessions = linkedMapOf<Long, Session>()
    private var nextSession = 0L
    private var previous: Thread.UncaughtExceptionHandler? = null
    private var installed: Thread.UncaughtExceptionHandler? = null
    private val urls = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    @Synchronized
    fun register(file: File, mode: String): Long {
        if (installed == null) {
            val delegate = Thread.getDefaultUncaughtExceptionHandler()
            previous = delegate
            val handler = Thread.UncaughtExceptionHandler { thread, error ->
                try {
                    recordCrash(thread, error)
                } catch (_: Throwable) {
                    // Recording must not replace normal fatal exception handling.
                } finally {
                    delegate?.uncaughtException(thread, error)
                }
            }
            Thread.setDefaultUncaughtExceptionHandler(handler)
            installed = handler
        }
        val id = ++nextSession
        sessions[id] = Session(file, mode)
        return id
    }

    @Synchronized
    fun unregister(id: Long) {
        sessions.remove(id)
        if (sessions.isNotEmpty()) return
        if (Thread.getDefaultUncaughtExceptionHandler() === installed) {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        installed = null
        previous = null
    }

    @Synchronized
    private fun recordCrash(thread: Thread, error: Throwable) {
        // Overlapping preview/dream sessions are listed, rather than guessing
        // which session caused an exception on a shared worker thread.
        sessions.values.groupBy { it.file }.forEach { (file, active) ->
            write(file, "ANDROSAVER POSTMORTEM\nTime: ${Date()}\n" +
                "Active modes: ${active.joinToString { it.mode }}\n" +
                "Thread: ${thread.name}\n${error.stackTraceToString()}")
        }
    }

    private fun recoverableFile(file: File) = File(file.parentFile, "${file.name}.recoverable")

    @Synchronized
    fun writeRecoverable(file: File, details: String) = write(recoverableFile(file), details)

    @Synchronized
    fun readReports(file: File): String? {
        val reports = listOfNotNull(
            read(File(file.parentFile, "${file.name}.interrupted"))?.let { it },
            read(file)?.let { "SAVED FATAL / LEGACY REPORT\n\n$it" },
            read(recoverableFile(file))?.let { "LATEST RECOVERABLE FAILURE\n\n$it" }
        )
        return reports.takeIf { it.isNotEmpty() }?.joinToString("\n\n────────\n\n")
    }

    /** Clear saved reports only; an active-session marker must remain owned by its session. */
    @Synchronized fun clearReports(file: File): Boolean {
        val reports = listOf(file, recoverableFile(file), File(file.parentFile, "${file.name}.interrupted"))
        var success = true
        reports.forEach { report ->
            if (report.exists() && !report.delete()) success = false
            val temporary = File(report.parentFile, "${report.name}.tmp")
            if (temporary.exists() && !temporary.delete()) success = false
        }
        return success
    }

    @Synchronized
    fun write(file: File, details: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            val safe = details.replace(urls, "[URL redacted]").take(64_000)
            FileOutputStream(temporary).use { stream ->
                stream.write(safe.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // Same-directory rename preserves the previous report if writing fails.
            if (!temporary.renameTo(file)) throw java.io.IOException("Cannot replace postmortem")
        } catch (_: Exception) {
            // Full disk and unavailable storage must not cause a second failure.
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun read(file: File): String? =
        if (file.isFile) file.bufferedReader().use { reader ->
            val buffer = CharArray(64_000)
            var count = 0
            while (count < buffer.size) {
                val read = reader.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            String(buffer, 0, count)
        } else null
}
