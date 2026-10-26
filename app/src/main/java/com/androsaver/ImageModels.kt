package com.androsaver

import android.net.Uri
import com.androsaver.source.ImageItem
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import java.io.File

/** One routing boundary for cache, device, bundled and remote image models. */
internal object ImageModels {
    fun forItem(item: ImageItem): Any {
        val uri = Uri.parse(item.url)
        return when (uri.scheme?.lowercase()) {
            "content" -> uri
            "file" -> if (uri.path?.startsWith("/android_asset/") == true) uri
                      else uri.path?.let(::File) ?: uri
            else -> {
                val headers = LazyHeaders.Builder()
                item.headers.forEach { (key, value) -> headers.addHeader(key, value) }
                item.insecureEndpoint?.let {
                    headers.addHeader(HttpClients.INSECURE_ENDPOINT_HEADER, HttpClients.insecureEndpointHeader(it))
                }
                GlideUrl(item.url, headers.build())
            }
        }
    }
}
