package com.androsaver.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalStorageSource(private val context: Context) : ImageSource {
    override val name = "Device Photos"
    override fun isConfigured() = true

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ImageItem>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val id   = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val uri  = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(ImageItem(url = uri.toString(), name = name))
                    count++
                }
            }
        } catch (_: Exception) {}
        items
    }
}
