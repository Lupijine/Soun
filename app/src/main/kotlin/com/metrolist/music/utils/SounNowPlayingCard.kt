/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A story-sized (9:16) "Now listening" card: the cover over a blurred version of itself, the
 * song and artist, a glass progress capsule with times, and the Soun mark at the bottom.
 */
object SounNowPlayingCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    suspend fun create(
        context: Context,
        coverUrl: String?,
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
    ): Bitmap = withContext(Dispatchers.Default) {
        val cover = coverUrl?.let { loadCover(context, it) }
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Background: the cover blurred by scaling it down hard and back up, then darkened.
        if (cover != null) {
            val tiny = cover.scale(18, 18, filter = true)
            val blurred = tiny.scale(WIDTH, WIDTH, filter = true)
            val matrix = Matrix().apply {
                val s = HEIGHT.toFloat() / blurred.height
                setScale(s, s)
                postTranslate((WIDTH - blurred.width * s) / 2f, 0f)
            }
            canvas.drawBitmap(blurred, matrix, paint)
        } else {
            canvas.drawColor(Color.rgb(46, 15, 38))
        }
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            intArrayOf(Color.argb(90, 10, 5, 10), Color.argb(150, 10, 5, 10), Color.argb(215, 10, 5, 10)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        // Cover with rounded corners and a soft shadow.
        val coverSize = 780f
        val coverLeft = (WIDTH - coverSize) / 2f
        val coverTop = 300f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)
        val radius = 64f
        paint.color = Color.argb(110, 0, 0, 0)
        paint.setShadowLayer(60f, 0f, 30f, Color.argb(140, 0, 0, 0))
        canvas.drawRoundRect(coverRect, radius, radius, paint)
        paint.clearShadowLayer()
        if (cover != null) {
            val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = Matrix().apply {
                val s = maxOf(coverSize / cover.width, coverSize / cover.height)
                setScale(s, s)
                postTranslate(coverLeft + (coverSize - cover.width * s) / 2f, coverTop + (coverSize - cover.height * s) / 2f)
            }
            shader.setLocalMatrix(m)
            paint.shader = shader
            paint.color = Color.WHITE
            canvas.drawRoundRect(coverRect, radius, radius, paint)
            paint.shader = null
        }
        // Glass rim on the cover.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(coverRect, radius, radius, paint)
        paint.style = Paint.Style.FILL

        // Title and artist.
        val textWidth = coverSize.toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 255, 255, 255)
            textSize = 44f
            typeface = Typeface.DEFAULT
        }
        var y = coverRect.bottom + 90f
        y += drawLine(canvas, title, titlePaint, coverLeft, y, textWidth, maxLines = 2) + 14f
        y += drawLine(canvas, artist, artistPaint, coverLeft, y, textWidth, maxLines = 1) + 60f

        // Glass progress capsule with times.
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val barRect = RectF(coverLeft, y, coverLeft + coverSize, y + 14f)
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(barRect, 7f, 7f, paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(barRect.left, barRect.top, barRect.left + barRect.width() * fraction, barRect.bottom), 7f, 7f, paint)
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 255, 255, 255)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timeY = barRect.bottom + 54f
        canvas.drawText(makeTime(positionMs), coverLeft, timeY, timePaint)
        val remaining = "-" + makeTime((durationMs - positionMs).coerceAtLeast(0))
        canvas.drawText(remaining, coverRect.right - timePaint.measureText(remaining), timeY, timePaint)

        // Soun mark at the bottom: a small lotus and the name inside a glass pill.
        drawBrand(canvas, paint)
        bitmap
    }

    /** Sends the card to the system share sheet. */
    fun share(context: Context, bitmap: Bitmap) {
        val uri = ComposeToImage.saveBitmapAsFile(context, bitmap, "soun_now_playing_${System.currentTimeMillis()}")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun loadCover(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context).data(url).size(1024).allowHardware(false).build()
        ImageLoader(context).execute(request).image?.toBitmap()
    }.getOrNull()

    private fun drawLine(canvas: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Int, maxLines: Int): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun drawBrand(canvas: Canvas, paint: Paint) {
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val name = "Soun"
        val lotus = 52f
        val gap = 18f
        val pillWidth = 48f + lotus + gap + namePaint.measureText(name) + 48f
        val pillHeight = 96f
        val pill = RectF((WIDTH - pillWidth) / 2f, HEIGHT - 190f, (WIDTH + pillWidth) / 2f, HEIGHT - 190f + pillHeight)
        paint.color = Color.argb(46, 255, 255, 255)
        canvas.drawRoundRect(pill, pillHeight / 2, pillHeight / 2, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.argb(80, 255, 255, 255)
        canvas.drawRoundRect(pill, pillHeight / 2, pillHeight / 2, paint)
        paint.style = Paint.Style.FILL

        // Lotus (same five petals as the app icon), in a 64-unit box scaled to `lotus` px.
        canvas.save()
        canvas.translate(pill.left + 48f, pill.centerY() - lotus / 2f)
        canvas.scale(lotus / 64f, lotus / 64f)
        paint.color = Color.WHITE
        val petals = listOf(
            "M32 8C40 18 42 30 32 46C22 30 24 18 32 8Z",
            "M30 46C20 40 14 30 16 18C24 22 29 32 30 46Z",
            "M34 46C44 40 50 30 48 18C40 22 35 32 34 46Z",
            "M30 49C20 51 10 47 4 38C14 34 24 38 30 49Z",
            "M34 49C44 51 54 47 60 38C50 34 40 38 34 49Z",
        )
        petals.forEach { canvas.drawPath(androidx.core.graphics.PathParser.createPathFromPathData(it) ?: Path(), paint) }
        canvas.restore()

        canvas.drawText(name, pill.left + 48f + lotus + gap, pill.centerY() + namePaint.textSize / 3f, namePaint)
    }

    private fun makeTime(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d".format(total / 60, total % 60)
    }
}
