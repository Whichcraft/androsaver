package com.androsaver.source

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Serves images bundled in `app/src/main/assets/default_images/`.
 * Activated automatically when no other image source is enabled.
 * Supports JPEG, PNG, WebP, GIF, BMP.
 */
class DefaultImagesSource(private val context: Context) : ImageSource {

    override val name = "Default Images"
    override fun isConfigured() = true

    override suspend fun getImageUrls(): List<ImageItem> = withContext(Dispatchers.IO) {
        try {
            val files = context.assets.list("default_images") ?: return@withContext emptyList()
            files
                .filter { it.matches(Regex(".*\\.(jpg|jpeg|png|webp|gif|bmp)", RegexOption.IGNORE_CASE)) }
                .map { filename ->
                    ImageItem(
                        url  = "file:///android_asset/default_images/$filename",
                        name = filename
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
