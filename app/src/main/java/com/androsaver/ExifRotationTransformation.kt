package com.androsaver

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Glide [BitmapTransformation] that rotates/flips a bitmap to match a stored EXIF orientation.
 * Used for images where orientation is read ahead of time (local MediaStore, cached files).
 * Remote JPEG images are left to Glide's Downsampler which reads EXIF from the HTTP stream.
 */
class ExifRotationTransformation(private val exifOrientation: Int) : BitmapTransformation() {

    override fun transform(pool: BitmapPool, source: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return source  // NORMAL or UNDEFINED — nothing to do
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated != source) pool.put(source)
        return rotated
    }

    override fun equals(other: Any?) = other is ExifRotationTransformation && other.exifOrientation == exifOrientation
    override fun hashCode() = exifOrientation
    override fun updateDiskCacheKey(digest: MessageDigest) {
        digest.update("ExifRotation$exifOrientation".toByteArray())
    }
}
