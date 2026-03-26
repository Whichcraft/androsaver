package com.androsaver.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.androsaver.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalStorageSource(private val context: Context) : ImageSource {
    override val name = "Device Photos"
    override fun isConfigured() = true

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ImageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.ORIENTATION
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder
            )?.use { cursor ->
                val idCol          = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol        = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val orientationCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val id          = cursor.getLong(idCol)
                    val name        = cursor.getString(nameCol) ?: ""
                    val degrees     = cursor.getInt(orientationCol)
                    val uri         = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(ImageItem(url = uri.toString(), name = name, orientation = degreesToExif(degrees)))
                    count++
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.w("LocalStorageSource", "MediaStore query failed: ${e.message}")
        }
        items
    }

    private fun degreesToExif(degrees: Int) = when (degrees) {
        90  -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }
}
