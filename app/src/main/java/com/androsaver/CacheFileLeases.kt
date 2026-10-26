package com.androsaver

import java.io.Closeable
import java.io.File

/** Process-wide leases also coordinate separate live/prefetch ImageCache instances. */
internal object CacheFileLeases {
    private val references = mutableMapOf<File, Int>()
    @Synchronized fun acquire(files: List<File>): Closeable {
        val paths = files.map { it.absoluteFile }.toSet()
        paths.forEach { references[it] = (references[it] ?: 0) + 1 }
        var closed = false
        return Closeable {
            synchronized(this) {
                if (!closed) {
                    closed = true
                    paths.forEach { file ->
                        val count = (references[file] ?: 1) - 1
                        if (count <= 0) references.remove(file) else references[file] = count
                    }
                }
            }
        }
    }
    @Synchronized fun contains(file: File) = references.containsKey(file.absoluteFile)
    @Synchronized fun inDirectory(directory: File): Set<File> =
        references.keys.filter { it.parentFile == directory.absoluteFile }.toSet()
}
