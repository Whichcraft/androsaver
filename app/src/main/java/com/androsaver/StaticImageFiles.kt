package com.androsaver

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/** Publish immutable image files before switching the persisted selection. */
internal object StaticImageFiles {
    private val mutex = Mutex()
    private const val MAX_BYTES = 50L * 1024 * 1024

    fun selectedFile(directory: File, path: String?): File? {
        val selected = path?.let { File(it) } ?: return null
        val legacy = File(directory, "background_image")
        val backup = File(directory, "background_image.previous")
        if (selected == legacy && !legacy.exists() && backup.isFile) backup.renameTo(legacy)
        return selected.takeIf { it.isFile }
    }

    suspend fun importImage(
        directory: File,
        selectedPath: () -> String?,
        open: () -> InputStream?,
        validImage: (File) -> Boolean,
        publish: (File) -> Boolean
    ): String? = mutex.withLock {
        if (!directory.isDirectory && !directory.mkdirs()) return@withLock null
        val previous = selectedPath()?.let { File(it) }
        // Recover the old two-rename format if an earlier process died mid-replacement.
        selectedFile(directory, previous?.absolutePath)
        val destination = File(directory, "background_${UUID.randomUUID()}.img")
        val temporary = File(directory, "${destination.name}.tmp")
        var published = false
        try {
            val input = open() ?: return@withLock null
            input.use { source ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) return@withLock null
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            if (!validImage(temporary)) return@withLock null
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(destination)) return@withLock null
            // Cancellation cannot split pointer publication from file ownership.
            withContext(NonCancellable) {
                if (!publish(destination)) return@withContext
                published = true
                // Retain the previous selection too, for in-flight readers.
                directory.listFiles()?.filter {
                    it != destination && it != previous &&
                        (it.name.startsWith("background_") || it.name == "background_image.previous")
                }?.forEach { it.delete() }
            }
            if (published) destination.absolutePath else null
        } finally {
            temporary.delete()
            if (!published) destination.delete()
        }
    }
}
