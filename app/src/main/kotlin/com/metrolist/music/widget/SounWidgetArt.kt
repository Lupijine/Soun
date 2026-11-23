/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.metrolist.music.R

/**
 * Artwork variants for the Liquid Glass widgets. RemoteViews can neither clip nor blur,
 * so the shapes and the album-tinted glass are baked into small bitmaps here.
 */
internal class SounWidgetCovers(
    /** Rounded square for the 4x2 cover. */
    val rounded: Bitmap,
    /** Circle for the 4x1 pill and the turntable disc. */
    val circular: Bitmap,
    /** Full-bleed art for the 2x2; the layout clips it on API 31+, older launchers get it pre-rounded. */
    val fill: Bitmap,
    /** Tiny blurred copy used as the album-tinted glass background, null for the placeholder. */
    val blurred: Bitmap?,
)

internal object SounWidgetArt {
    private const val COVER_RADIUS_FRACTION = 20f / 148f
    private const val FILL_RADIUS_FRACTION = 28f / 160f
    private const val BLUR_SIZE = 48
    private const val BLUR_RADIUS = 4
    private const val BLUR_PASSES = 3
    private const val BLUR_SATURATION = 1.35f
    private const val PLACEHOLDER_SIZE = 192

    @Volatile
    private var placeholderCovers: SounWidgetCovers? = null

    fun coversFor(art: Bitmap): SounWidgetCovers {
        val square = squareCrop(art)
        return SounWidgetCovers(
            rounded = rounded(square, COVER_RADIUS_FRACTION),
            circular = rounded(square, 0.5f),
            fill = fillFor(square),
            blurred = blurred(square),
        )
    }

    fun placeholderCovers(context: Context): SounWidgetCovers =
        placeholderCovers ?: synchronized(this) {
            placeholderCovers ?: placeholder(context).let { art ->
                SounWidgetCovers(
                    rounded = rounded(art, COVER_RADIUS_FRACTION),
                    circular = rounded(art, 0.5f),
                    fill = fillFor(art),
                    blurred = null,
                )
            }.also { placeholderCovers = it }
        }

    /** Rounds a square crop of [bitmap]; [radiusFraction] is the corner radius relative to its side. */
    fun rounded(bitmap: Bitmap, radiusFraction: Float): Bitmap {
        val square = squareCrop(bitmap)
        val size = square.width
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size * radiusFraction
        Canvas(output).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        if (square !== bitmap) square.recycle()
        return output
    }

    private fun fillFor(square: Bitmap): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) square else rounded(square, FILL_RADIUS_FRACTION)

    private fun squareCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        if (bitmap.width == size && bitmap.height == size) return bitmap
        return Bitmap.createBitmap(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)
    }

    /**
     * Downscales the art to [BLUR_SIZE] px, boosts saturation and box-blurs it. The launcher's
     * bilinear upscaling of such a small bitmap smooths it into a soft glass tint.
     */
    private fun blurred(square: Bitmap): Bitmap {
        val small = Bitmap.createBitmap(BLUR_SIZE, BLUR_SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(BLUR_SATURATION) })
        }
        Canvas(small).drawBitmap(
            square,
            Rect(0, 0, square.width, square.height),
            Rect(0, 0, BLUR_SIZE, BLUR_SIZE),
            paint,
        )
        val pixels = IntArray(BLUR_SIZE * BLUR_SIZE)
        val scratch = IntArray(pixels.size)
        small.getPixels(pixels, 0, BLUR_SIZE, 0, 0, BLUR_SIZE, BLUR_SIZE)
        repeat(BLUR_PASSES) {
            boxBlur(pixels, scratch, BLUR_SIZE, BLUR_SIZE, horizontal = true)
            boxBlur(scratch, pixels, BLUR_SIZE, BLUR_SIZE, horizontal = false)
        }
        small.setPixels(pixels, 0, BLUR_SIZE, 0, 0, BLUR_SIZE, BLUR_SIZE)
        return small
    }

    // One sliding-window pass along rows or columns, clamping at the edges.
    private fun boxBlur(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        horizontal: Boolean,
    ) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val step = if (horizontal) 1 else width
        val window = BLUR_RADIUS * 2 + 1
        for (line in 0 until lines) {
            val start = if (horizontal) line * width else line
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -BLUR_RADIUS..BLUR_RADIUS) {
                val p = src[start + i.coerceIn(0, length - 1) * step]
                a += p ushr 24
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                dst[start + i * step] =
                    ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val leaving = src[start + (i - BLUR_RADIUS).coerceIn(0, length - 1) * step]
                val entering = src[start + (i + BLUR_RADIUS + 1).coerceIn(0, length - 1) * step]
                a += (entering ushr 24) - (leaving ushr 24)
                r += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
                g += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    /** Plum-to-pink tile with the lotus mark, used when a song has no artwork. */
    private fun placeholder(context: Context): Bitmap {
        val size = PLACEHOLDER_SIZE.toFloat()
        val bitmap = Bitmap.createBitmap(PLACEHOLDER_SIZE, PLACEHOLDER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size, size,
                intArrayOf(0xFF3B1B45.toInt(), 0xFF8E4A86.toInt(), 0xFFE59AB4.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size, size, paint)
        context.getDrawable(R.drawable.small_icon)?.let { mark ->
            val markSize = PLACEHOLDER_SIZE * 3 / 8
            val offset = (PLACEHOLDER_SIZE - markSize) / 2
            mark.setBounds(offset, offset, offset + markSize, offset + markSize)
            mark.draw(canvas)
        }
        return bitmap
    }
}
