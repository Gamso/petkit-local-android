package org.petkitlocal.feature.pets.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The photo's files and pixels, on the phone. The camera app writes into a file of ours in the
 * cache, shared with it through this module's FileProvider for that capture only; a photo from
 * the gallery is read through its content URI (the photo picker needs no permission). Nothing is
 * kept: only the square crop leaves the phone, as a JPEG.
 */
internal object PetPhotoFiles {
    private const val DIRECTORY = "pet-photos"
    private const val AUTHORITY_SUFFIX = ".petphotos"

    /** The longest side a photo is decoded at: plenty for a square of the head, and bounded in memory. */
    private const val MAX_DECODED_SIDE = 2048

    /** The side of the JPEG sent: above the server's 224 px, so its own scaling has room. */
    private const val MAX_SENT_SIDE = 640
    private const val JPEG_QUALITY = 92

    /** A fresh file for the camera app to write the next photo into, as the URI to hand it. */
    fun newCaptureUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        // One capture at a time: the previous one is no longer needed.
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    /**
     * The photo at [source], upright (ImageDecoder applies the EXIF orientation a camera writes)
     * and at most [MAX_DECODED_SIDE] on its longest side; null when it cannot be read.
     */
    suspend fun decode(context: Context, source: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageSource = ImageDecoder.createSource(context.contentResolver, source.toUri())
            ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                // Software pixels: the crop reads them back to encode the JPEG.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_DECODED_SIDE) {
                    val scale = MAX_DECODED_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** The [square] of [bitmap] as a JPEG, scaled down to [MAX_SENT_SIDE] if it is larger. */
    suspend fun cropJpeg(bitmap: Bitmap, square: CropSquare): ByteArray = withContext(Dispatchers.Default) {
        val side = min(square.side, min(bitmap.width - square.left, bitmap.height - square.top)).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bitmap, square.left, square.top, side, side)
        val tooLarge = side > MAX_SENT_SIDE
        val sent = if (tooLarge) cropped.scale(MAX_SENT_SIDE, MAX_SENT_SIDE) else cropped
        ByteArrayOutputStream().use { out ->
            sent.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }
}
