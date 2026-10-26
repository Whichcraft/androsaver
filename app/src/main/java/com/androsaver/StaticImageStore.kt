package com.androsaver

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.androsaver.source.ImageItem
import okhttp3.Request
import java.io.File

/** Imports and durably selects a new immutable image without overwriting the working copy. */
object StaticImageStore {

    suspend fun copyUri(context: Context, uri: Uri): String? =
        copyStream(context, uri.lastPathSegment ?: "Selected image") { context.contentResolver.openInputStream(uri) }

    suspend fun copyItem(context: Context, item: ImageItem): String? {
        val url = item.url
        val uri = Uri.parse(url)
        return when (uri.scheme?.lowercase()) {
            "content" -> copyStream(context, item.name) { context.contentResolver.openInputStream(uri) }
            "file" -> {
                val path = uri.path ?: return null
                copyStream(context, item.name) {
                    if (path.startsWith("/android_asset/")) context.assets.open(path.removePrefix("/android_asset/"))
                    else File(path).inputStream()
                }
            }
            else -> {
                val request = Request.Builder().url(url).apply {
                    item.headers.forEach { (name, value) -> addHeader(name, value) }
                }.build()
                HttpClients.forImageItem(context, item).newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    copyStream(context, item.name) { body.byteStream() }
                }
            }
        }
    }

    private suspend fun copyStream(
        context: Context, displayName: String, open: () -> java.io.InputStream?
    ): String? {
        val prefs = Prefs.get(context)
        return StaticImageFiles.importImage(
            directory = File(context.filesDir, "static_background"),
            selectedPath = { prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null) },
            open = open,
            validImage = ::isDecodableImage,
            publish = { file ->
                val previousPath = prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null)
                val previousName = prefs.getString(Prefs.STATIC_IMAGE_DISPLAY_NAME, null)
                val previousUri = prefs.getString(Prefs.STATIC_IMAGE_URI, null)
                val saved = prefs.edit().remove(Prefs.STATIC_IMAGE_URI)
                    .putString(Prefs.STATIC_IMAGE_LOCAL_PATH, file.absolutePath)
                    .putString(Prefs.STATIC_IMAGE_DISPLAY_NAME, displayName.ifBlank { "Selected image" })
                    .commit()
                if (!saved) {
                    // SharedPreferences updates memory even if its disk commit fails.
                    prefs.edit().putString(Prefs.STATIC_IMAGE_LOCAL_PATH, previousPath)
                        .putString(Prefs.STATIC_IMAGE_DISPLAY_NAME, previousName)
                        .putString(Prefs.STATIC_IMAGE_URI, previousUri).commit()
                }
                saved
            }
        )
    }

    private fun isDecodableImage(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}
