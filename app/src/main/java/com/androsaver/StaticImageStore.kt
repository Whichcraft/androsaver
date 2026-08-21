package com.androsaver

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.androsaver.source.ImageItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import java.io.File

/** Imports a selected image without destroying the currently working copy. */
object StaticImageStore {
    private const val MAX_BYTES = 50L * 1024 * 1024

    suspend fun copyUri(context: Context, uri: Uri): String? =
        copyStream(context) { context.contentResolver.openInputStream(uri) }

    suspend fun copyItem(context: Context, item: ImageItem): String? {
        val url = item.url
        return when {
            url.startsWith("content://") -> copyUri(context, Uri.parse(url))
            url.startsWith("file://") -> {
                val path = Uri.parse(url).path ?: return null
                copyStream(context) { File(path).inputStream() }
            }
            else -> {
                val request = Request.Builder().url(url).apply {
                    item.headers.forEach { (name, value) -> addHeader(name, value) }
                }.build()
                HttpClients.forHost(context, request.url.host).newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    copyStream(context) { body.byteStream() }
                }
            }
        }
    }

    private suspend fun copyStream(context: Context, open: () -> java.io.InputStream?): String? {
        val directory = File(context.filesDir, "static_background")
        if (!directory.exists() && !directory.mkdirs()) return null
        val destination = File(directory, "background_image")
        val temporary = File.createTempFile("background_image_", ".tmp", directory)
        return try {
            val input = open() ?: return null
            input.use { source ->
                temporary.outputStream().use { target ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) return null
                        target.write(buffer, 0, count)
                    }
                }
            }
            if (!isDecodableImage(temporary)) return null
            val backup = File(directory, "background_image.previous")
            if (backup.exists()) backup.delete()
            val hadOld = destination.exists()
            if (hadOld && !destination.renameTo(backup)) return null
            if (!temporary.renameTo(destination)) {
                if (hadOld) backup.renameTo(destination)
                return null
            }
            backup.delete()
            destination.absolutePath
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun isDecodableImage(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}
