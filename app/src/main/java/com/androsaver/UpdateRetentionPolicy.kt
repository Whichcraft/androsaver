package com.androsaver

import java.io.File
import java.io.IOException

internal class UpdateStagingFullException : IOException("Recent update stages occupy the storage budget")

/** Recent stages may still be owned by Android's external package installer. */
internal class UpdateRetentionPolicy(
    private val maxBytes: Long = 200L * 1024 * 1024,
    private val maxFiles: Int = 3,
    private val protectedAgeMs: Long = 24L * 60 * 60 * 1000
) {
    init { require(maxBytes >= 0 && maxFiles > 0 && protectedAgeMs >= 0) }

    fun prune(directory: File, destination: File, incomingBytes: Long, installedVersion: Int?,
              now: Long, enforce: Boolean = true, activeTemporary: File? = null) {
        if (incomingBytes !in 0..maxBytes) throw IOException("Update exceeds staging budget")
        val artifacts = directory.listFiles().orEmpty().filter { it.isFile && it.extension == "apk" && it != destination }
            .sortedBy { it.lastModified() }.toMutableList()
        val name = Regex("androsaver-([0-9]+)-[0-9a-f]{64}\\.apk")
        // Installation of a newer app makes these stages obsolete, even if recent.
        artifacts.toList().forEach { file ->
            val version = name.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            if (installedVersion != null && version != null && version <= installedVersion && file.delete()) artifacts.remove(file)
        }
        fun exceedsBudget() = artifacts.size + 1 > maxFiles || artifacts.sumOf { it.length() } + incomingBytes > maxBytes
        artifacts.toList().forEach { file ->
            if (exceedsBudget() && now - file.lastModified() >= protectedAgeMs && file.delete()) artifacts.remove(file)
        }
        directory.listFiles().orEmpty().filter {
            it != activeTemporary && it.isFile && it.name.startsWith("download-") && it.extension == "part" && now - it.lastModified() >= protectedAgeMs
        }.forEach { it.delete() }
        // Do not evict recent stages just to make room for another download.
        if (enforce && exceedsBudget()) throw UpdateStagingFullException()
    }
}
