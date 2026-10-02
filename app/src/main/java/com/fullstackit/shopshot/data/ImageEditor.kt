package com.fullstackit.shopshot.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How the user has framed a photo in the editor.
 *
 * The model is "a fixed window, with the image moving underneath it", which is what makes
 * cropping and zooming the same gesture: pinching in crops tighter. [scale] and [offset] are
 * expressed relative to the crop window, not to the bitmap, so the same numbers describe the
 * framing whatever resolution the image is decoded at. That is what lets a preview rendered
 * from a downsampled bitmap export identically at full resolution.
 */
data class CropFraming(
    /** 1f means the image exactly covers the window; larger means zoomed in. */
    val scale: Float = 1f,
    /** Pan, as a fraction of the crop window's width and height. */
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    /** Quarter turns the user applied, 0..3. */
    val quarterTurns: Int = 0,
    /** Window shape, width / height. Null means "whatever the source already is". */
    val aspectRatio: Float? = null,
)

/** The longest edge an exported photo may have; keeps memory sane on big sensors. */
private const val MAX_EXPORT_EDGE = 4096

class ImageEditor(private val context: Context) {

    private val resolver get() = context.contentResolver

    /**
     * Decodes a copy small enough to push around at 60fps. Editing a 12 megapixel photo
     * directly would spend 48MB of heap on something the screen cannot show anyway.
     */
    suspend fun loadForDisplay(uri: Uri, maxEdge: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null
            applyExifRotation(uri, decoded)
        }

    /**
     * Renders the framed result at full resolution and writes it as a new JPEG.
     *
     * Returns the new photo's uri, or null if it could not be written.
     */
    suspend fun exportCrop(
        source: Uri,
        framing: CropFraming,
        targetFolder: String,
        displayName: String,
        drawOverlays: ((Canvas, Int, Int) -> Unit)? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        val full = loadForDisplay(source, MAX_EXPORT_EDGE) ?: return@withContext null
        val rendered = runCatching { render(full, framing, drawOverlays) }.getOrNull()
        if (full != rendered) full.recycle()
        val bitmap = rendered ?: return@withContext null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaRepository.relativePathFor(targetFolder),
            )
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val dest = runCatching { resolver.insert(collection, values) }.getOrNull()
        if (dest == null) {
            bitmap.recycle()
            return@withContext null
        }

        val ok = runCatching {
            resolver.openOutputStream(dest)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            } ?: false
        }.getOrDefault(false)
        bitmap.recycle()

        if (ok) dest else {
            runCatching { resolver.delete(dest, null, null) }
            null
        }
    }

    /**
     * Applies rotation, then carves out the region the crop window was showing.
     *
     * Everything is derived from the same cover-fit rule the preview uses, so what the user
     * framed is what lands on disk.
     */
    private fun render(
        source: Bitmap,
        framing: CropFraming,
        drawOverlays: ((Canvas, Int, Int) -> Unit)?,
    ): Bitmap {
        val rotated = rotate(source, framing.quarterTurns)

        val srcW = rotated.width.toFloat()
        val srcH = rotated.height.toFloat()
        val windowAspect = framing.aspectRatio ?: (srcW / srcH)

        // The crop window, measured in source pixels at scale 1 (the cover fit).
        val coverW: Float
        val coverH: Float
        if (srcW / srcH > windowAspect) {
            // Source is wider than the window, so height is the limiting edge.
            coverH = srcH
            coverW = srcH * windowAspect
        } else {
            coverW = srcW
            coverH = srcW / windowAspect
        }

        val cropW = coverW / framing.scale
        val cropH = coverH / framing.scale

        // Pan is a fraction of the window, so it converts straight into source pixels here.
        val centreX = srcW / 2f - framing.offsetXFraction * cropW
        val centreY = srcH / 2f - framing.offsetYFraction * cropH

        var left = (centreX - cropW / 2f)
        var top = (centreY - cropH / 2f)
        left = left.coerceIn(0f, max(0f, srcW - cropW))
        top = top.coerceIn(0f, max(0f, srcH - cropH))

        val rect = Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + cropW).roundToInt().coerceAtMost(rotated.width),
            (top + cropH).roundToInt().coerceAtMost(rotated.height),
        )
        val outW = max(1, rect.width())
        val outH = max(1, rect.height())

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(rotated, rect, Rect(0, 0, outW, outH), null)
        drawOverlays?.invoke(canvas, outW, outH)

        if (rotated != source) rotated.recycle()
        return out
    }

    private fun rotate(bitmap: Bitmap, quarterTurns: Int): Bitmap {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return bitmap
        val matrix = Matrix().apply { postRotate(90f * turns) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Phone cameras commonly store the sensor image unrotated and record the orientation in
     * EXIF instead. Without this the editor would show a sideways photo that looks upright
     * everywhere else.
     */
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    /**
     * Copies a chosen logo into app storage and returns a uri that keeps working.
     *
     * The photo picker hands back a uri the app may read now and not after a restart, so
     * storing that uri would give her a watermark that silently stopped appearing.
     */
    suspend fun importWatermark(source: Uri): Uri? = withContext(Dispatchers.IO) {
        val bitmap = loadForDisplay(source, maxEdge = 1024) ?: return@withContext null
        val file = java.io.File(context.filesDir, "watermark.png")
        val ok = runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        bitmap.recycle()
        if (ok) Uri.fromFile(file) else null
    }

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (min(width, height) / sample > maxEdge || max(width, height) / sample > maxEdge * 2) {
            sample *= 2
        }
        return sample
    }
}
