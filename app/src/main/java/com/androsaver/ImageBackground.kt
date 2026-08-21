package com.androsaver

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Applies the background shown through fitted or centered images. */
object ImageBackground {
    const val MANUAL = "manual"
    const val AUTO = "auto"

    fun apply(view: View, drawable: Drawable?, mode: String?, manualColor: Int) {
        if (mode != AUTO) {
            view.setBackgroundColor(manualColor)
            return
        }

        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            view.setBackgroundColor(manualColor)
            return
        }

        val topLeft = sample(bitmap, 0.08f, 0.08f)
        val center = sample(bitmap, 0.50f, 0.50f)
        val bottomRight = sample(bitmap, 0.92f, 0.92f)
        view.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(darken(topLeft), darken(center), darken(bottomRight))
        )
    }

    private fun sample(bitmap: Bitmap, xFraction: Float, yFraction: Float): Int {
        val x = (bitmap.width * xFraction).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * yFraction).toInt().coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }

    private fun darken(color: Int): Int = Color.rgb(
        (Color.red(color) * 0.62f).toInt(),
        (Color.green(color) * 0.62f).toInt(),
        (Color.blue(color) * 0.62f).toInt()
    )
}
