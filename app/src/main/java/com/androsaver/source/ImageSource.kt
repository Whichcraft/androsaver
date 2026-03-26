package com.androsaver.source

data class ImageItem(
    val url: String,
    val name: String = "",
    val headers: Map<String, String> = emptyMap(),
    /** ExifInterface.ORIENTATION_* constant; 0 = unknown (let Glide handle it). */
    val orientation: Int = 0
)

interface ImageSource {
    val name: String
    suspend fun getImageUrls(): List<ImageItem>
    fun isConfigured(): Boolean
}
