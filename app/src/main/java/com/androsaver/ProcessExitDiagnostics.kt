package com.androsaver

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.util.Date

/** Read Android-owned exit evidence on IO; API 21–29 retain the session-marker fallback. */
internal object ProcessExitDiagnostics {
    private val lock = Any()
    fun collect(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        synchronized(lock) {
            try {
                val prefs = context.getSharedPreferences("process_exit_diagnostics", Context.MODE_PRIVATE)
                val last = prefs.getLong("last_timestamp", 0L)
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                    .filter { it.timestamp > last && it.processName == context.packageName }
                val latest = exits.maxOfOrNull { it.timestamp } ?: return
                val exit = exits.filter { it.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                    ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE) }
                    .maxByOrNull { it.timestamp }
                if (exit != null) {
                    val trace = try {
                        exit.traceInputStream?.use { input ->
                            if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                                val data = ByteArray(64 * 1024)
                                var size = 0
                                while (size < data.size) { val n = input.read(data, size, data.size - size); if (n < 0) break; size += n }
                                // Stack frames only: discard arbitrary thread/exception messages and application data.
                                stackFrames(String(data, 0, size, Charsets.UTF_8))
                            } else "Android native/system trace is available; raw binary trace is not copied."
                        }
                    } catch (_: Exception) { null }
                    val report = "ANDROID PROCESS EXIT\nTime: ${Date(exit.timestamp)}\nReason: ${reason(exit.reason)}\n" +
                        "Status / signal: ${exit.status}\nPSS: ${exit.pss} KiB; RSS: ${exit.rss} KiB\n" +
                        (trace?.takeIf { it.isNotBlank() } ?: "No usable system trace available.")
                    ScreensaverPostmortem.write(File(context.noBackupFilesDir, "screensaver-postmortem.txt.exit"), report)
                }
                prefs.edit().putLong("last_timestamp", latest).commit()
            } catch (_: Exception) { /* System evidence is optional and must never prevent startup. */ }
        }
    }
    internal fun stackFrames(trace: String): String = trace.lineSequence().map { it.trim() }
        .filter { it.startsWith("at ") }.take(150).joinToString("\n")
    internal fun reason(value: Int): String = when (value) {
        3 -> "LOW_MEMORY"; 4 -> "JAVA_CRASH"; 5 -> "NATIVE_CRASH"; 6 -> "ANR"; 9 -> "EXCESSIVE_RESOURCE_USAGE"
        else -> "SYSTEM_REASON_$value"
    }
}
