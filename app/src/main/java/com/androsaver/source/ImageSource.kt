package com.androsaver.source

data class ImageItem(
    val url: String,
    val name: String = "",
    val headers: Map<String, String> = emptyMap()
)

interface ImageSource {
    val name: String
    suspend fun getImageUrls(): List<ImageItem>
    fun isConfigured(): Boolean
}
