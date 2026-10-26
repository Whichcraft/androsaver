package com.androsaver

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/** Serialised staging with per-attempt temporary files and immutable published APKs. */
internal object UpdateDownloadFiles {
    private val mutex = Mutex()

    suspend fun prepare(
        directory: File,
        artifactName: String,
        validate: suspend (File) -> Unit,
        download: suspend (File) -> Unit
    ): File = mutex.withLock {
        require(File(artifactName).name == artifactName && artifactName.endsWith(".apk"))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create update directory")
        val destination = File(directory, artifactName)
        if (destination.isFile) {
            try {
                validate(destination)
                return@withLock destination
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                if (!destination.delete()) throw IOException("Cannot replace invalid cached update")
            }
        }
        val temporary = File.createTempFile("download-", ".part", directory)
        try {
            download(temporary)
            currentCoroutineContext().ensureActive()
            validate(temporary)
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(destination)) throw IOException("Cannot publish verified update")
            destination
        } finally {
            temporary.delete()
        }
    }
}
