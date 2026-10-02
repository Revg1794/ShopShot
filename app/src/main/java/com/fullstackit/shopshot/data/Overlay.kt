package com.fullstackit.shopshot.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Something stamped on top of the photo.
 *
 * Every measurement is a fraction of the crop window rather than a pixel count, for the same
 * reason the crop framing is: the editor previews a downsampled copy but exports at full
 * resolution, and fractions mean the same thing at both sizes. Position is the centre, so
 * resizing an overlay grows it around where it sits instead of dragging it off.
 */
sealed interface Overlay {
    val id: Long
    val centreX: Float
    val centreY: Float

    data class Text(
        override val id: Long,
        override val centreX: Float = 0.5f,
        override val centreY: Float = 0.85f,
        val text: String,
        /** Cap height as a fraction of the window's height. */
        val sizeFraction: Float = 0.08f,
        val colorArgb: Int = 0xFFFFFFFF.toInt(),
        val shadow: Boolean = true,
    ) : Overlay

    data class Logo(
        override val id: Long,
        override val centreX: Float = 0.5f,
        override val centreY: Float = 0.85f,
        /** Width as a fraction of the window's width. */
        val sizeFraction: Float = 0.3f,
        val alpha: Float = 1f,
        val uri: Uri,
    ) : Overlay
}

/**
 * Draws overlays onto a canvas of a given size.
 *
 * The editor preview and the export both go through here, so what she positions is what gets
 * written. Doing the preview with Compose text and the export with [Paint] would have let the
 * two drift apart in exactly the way that is hardest to notice before saving.
 */
class OverlayRenderer(private val context: Context) {

    private val logoCache = HashMap<String, Bitmap?>()

    fun draw(canvas: Canvas, width: Int, height: Int, overlays: List<Overlay>) {
        overlays.forEach { overlay ->
            when (overlay) {
                is Overlay.Text -> drawText(canvas, width, height, overlay)
                is Overlay.Logo -> drawLogo(canvas, width, height, overlay)
            }
        }
    }

    private fun drawText(canvas: Canvas, width: Int, height: Int, item: Overlay.Text) {
        if (item.text.isBlank()) return
        val paint = textPaint(height, item)
        val bounds = Rect()
        paint.getTextBounds(item.text, 0, item.text.length, bounds)

        val x = item.centreX * width
        // drawText places the baseline, so shift by half the glyph height to centre it.
        val y = item.centreY * height + bounds.height() / 2f
        canvas.drawText(item.text, x, y, paint)
    }

    /** Public so the preview can measure a label without duplicating the paint setup. */
    fun textPaint(height: Int, item: Overlay.Text): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = item.colorArgb
        textSize = max(1f, item.sizeFraction * height)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (item.shadow) {
            // Product photos are often pale, so white text needs a lift to stay readable.
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x99000000.toInt())
        }
    }

    private fun drawLogo(canvas: Canvas, width: Int, height: Int, item: Overlay.Logo) {
        val bitmap = logo(item.uri) ?: return
        val drawnW = item.sizeFraction * width
        val drawnH = drawnW * bitmap.height / bitmap.width
        val left = item.centreX * width - drawnW / 2f
        val top = item.centreY * height - drawnH / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (item.alpha.coerceIn(0f, 1f) * 255).roundToInt()
        }
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + drawnW, top + drawnH),
            paint,
        )
    }

    /** Logos are small and reused on every frame of a drag, so decoding once matters. */
    private fun logo(uri: Uri): Bitmap? = logoCache.getOrPut(uri.toString()) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    fun forget(uri: Uri) {
        logoCache.remove(uri.toString())
    }
}
