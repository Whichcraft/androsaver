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
    ): File = prepareBounded(directory, artifactName, 0, null, validate, download)

    suspend fun prepareBounded(
        directory: File,
        artifactName: String,
        expectedBytes: Long,
        installedVersion: Int?,
        validate: suspend (File) -> Unit,
        download: suspend (File) -> Unit,
        retention: UpdateRetentionPolicy = UpdateRetentionPolicy(),
        now: () -> Long = { System.currentTimeMillis() }
    ): File = mutex.withLock {
        require(File(artifactName).name == artifactName && artifactName.endsWith(".apk"))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create update directory")
        val destination = File(directory, artifactName)
        if (destination.isFile) {
            val valid = try {
                validate(destination)
                true
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                if (!destination.delete()) throw IOException("Cannot replace invalid cached update")
                false
            }
            if (valid) {
                retention.prune(directory, destination, destination.length(), installedVersion, now(), enforce = false)
                destination.setLastModified(now())
                return@withLock destination
            }
        }
        retention.prune(directory, destination, expectedBytes, installedVersion, now())
        val temporary = File.createTempFile("download-", ".part", directory)
        try {
            download(temporary)
            currentCoroutineContext().ensureActive()
            validate(temporary)
            currentCoroutineContext().ensureActive()
            retention.prune(directory, destination, temporary.length(), installedVersion, now(), activeTemporary = temporary)
            if (!temporary.renameTo(destination)) throw IOException("Cannot publish verified update")
            destination.setLastModified(now())
            destination
        } finally {
            temporary.delete()
        }
    }
}
