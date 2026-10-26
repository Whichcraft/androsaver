package com.androsaver

import java.io.File
import java.util.Date

/** Detect unclean process endings without guessing a cause or requiring a JVM crash. */
internal class SessionJournal {
    data class Start(val id: Long, val previousInterruption: String?)
    private data class Active(val file: File, val mode: String)
    private val active = linkedMapOf<Long, Active>()
    private var sequence = 0L
    private fun marker(file: File) = File(file.parentFile, "${file.name}.active")
    private fun interruption(file: File) = File(file.parentFile, "${file.name}.interrupted")

    @Synchronized fun begin(file: File, mode: String): Start {
        val previous = if (active.values.none { it.file == file }) {
            try { ScreensaverPostmortem.read(marker(file)) } catch (_: Exception) { null }
        } else null
        val report = previous?.let {
            "PREVIOUS SESSION ENDED WITHOUT A CLEAN SHUTDOWN\n" +
                "Cause unavailable. This can also happen when Android terminates the process.\n" +
                "Consult any captured fatal report separately.\nPrevious session metadata:\n$it"
        }
        if (report != null) ScreensaverPostmortem.write(interruption(file), report)
        val id = ++sequence
        active[id] = Active(file, mode)
        writeMarker(file)
        return Start(id, report)
    }

    @Synchronized fun end(id: Long) {
        val session = active.remove(id) ?: return
        if (active.values.any { it.file == session.file }) writeMarker(session.file)
        else try { marker(session.file).delete() } catch (_: Exception) { }
    }

    private fun writeMarker(file: File) {
        ScreensaverPostmortem.write(marker(file), "Started: ${Date()}\nActive modes: " +
            active.values.filter { it.file == file }.joinToString { it.mode })
    }
}
