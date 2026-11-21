package org.petkitlocal.feature.live

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.core.graphics.createBitmap
import androidx.media3.common.VideoSize
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The frame [view] shows, as a JPEG at the video's own size ([video], if known), or null when
 * there is none to copy: not laid out yet, no surface, or the copy failed.
 */
internal suspend fun captureJpeg(view: SurfaceView, video: VideoSize): ByteArray? {
    if (view.width == 0 || view.height == 0 || !view.holder.surface.isValid) return null
    val width = video.width.takeIf { it > 0 } ?: view.width
    val height = video.height.takeIf { it > 0 } ?: view.height
    val bitmap = createBitmap(width, height)
    val copied = suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            view,
            bitmap,
            { result -> continuation.resume(result == PixelCopy.SUCCESS) },
            Handler(Looper.getMainLooper()),
        )
    }
    if (!copied) {
        bitmap.recycle()
        return null
    }
    return withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}

private const val JPEG_QUALITY = 92
