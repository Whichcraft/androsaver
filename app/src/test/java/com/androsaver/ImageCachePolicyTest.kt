package com.androsaver

import com.androsaver.source.ImageItem
import org.junit.Assert.*
import org.junit.Test

class ImageCachePolicyTest {
    @Test fun identicalUrlsAndVersionsCannotShareAnAccountSignature() {
        val image = ImageItem("https://example.invalid/photo", stableId = "photo", sourceScope = "one", contentVersion = "v1")
        assertNotEquals(ImageCachePolicy.signature(image), ImageCachePolicy.signature(image.copy(sourceScope = "two")))
        assertNotEquals(ImageCachePolicy.signature(image), ImageCachePolicy.signature(image.copy(contentVersion = "v2")))
        assertEquals(ImageCachePolicy.signature(image), ImageCachePolicy.signature(image.copy(headers = mapOf("Authorization" to "nonfunctional-placeholder"))))
    }
}
