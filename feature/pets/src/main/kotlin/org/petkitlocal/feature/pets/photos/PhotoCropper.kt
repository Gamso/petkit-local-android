package org.petkitlocal.feature.pets.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square of the photo, in its pixels. */
data class CropSquare(val left: Int, val top: Int, val side: Int)

/**
 * How the photo lies under the crop frame: [zoom] from 1 (the photo just covers the frame) up to
 * [MAX_ZOOM], and [offset], the photo's centre from the frame's, in view pixels. The photo always
 * covers the frame, so every square it gives is all photo.
 */
@Stable
class CropState(val imageWidth: Int, val imageHeight: Int) {
    var zoom by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** The frame's side in view pixels, once the cropper is laid out. */
    var frame by mutableFloatStateOf(0f)
        internal set

    /** View pixels per photo pixel, for a frame [frame] view pixels wide. */
    fun scale(frame: Float = this.frame): Float = max(frame / imageWidth, frame / imageHeight) * zoom

    /** Moves the photo by [pan] and zooms it by [zoomChange], keeping the frame covered. */
    fun transform(pan: Offset, zoomChange: Float, frame: Float = this.frame) {
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = clamp(offset + pan, frame)
    }

    /** A step of the zoom buttons: TalkBack has no pinch. */
    fun zoomBy(factor: Float) = transform(Offset.Zero, factor)

    private fun clamp(offset: Offset, frame: Float): Offset {
        val scale = scale(frame)
        val maxX = (imageWidth * scale - frame) / 2
        val maxY = (imageHeight * scale - frame) / 2
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /** The square of the photo inside the frame. */
    fun square(frame: Float = this.frame): CropSquare {
        val scale = scale(frame)
        val photoLeft = -imageWidth * scale / 2 + offset.x
        val photoTop = -imageHeight * scale / 2 + offset.y
        val side = (frame / scale).roundToInt().coerceIn(1, min(imageWidth, imageHeight))
        val left = ((-frame / 2 - photoLeft) / scale).roundToInt().coerceIn(0, imageWidth - side)
        val top = ((-frame / 2 - photoTop) / scale).roundToInt().coerceIn(0, imageHeight - side)
        return CropSquare(left, top, side)
    }

    companion object {
        const val MAX_ZOOM = 8f

        /** The frame's share of the cropper's width: the rest shows what lies around, dimmed. */
        const val FRAME_SHARE = 0.78f
    }
}

@Composable
fun rememberCropState(image: ImageBitmap): CropState = remember(image) { CropState(image.width, image.height) }

/** The frame's side in view pixels, for a cropper [width] wide. */
internal fun cropFrame(width: Float): Float = width * CropState.FRAME_SHARE

/**
 * The photo under a square frame, moved and zoomed by touch; what lies outside the frame is
 * dimmed, and a circle in it says where the head goes. Square itself, as wide as it is given.
 */
@Composable
internal fun PhotoCropper(image: ImageBitmap, state: CropState, description: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clipToBounds()
            .semantics { contentDescription = description },
    ) {
        val width = with(density) { maxWidth.toPx() }
        val frame = cropFrame(width)
        SideEffect { state.frame = frame }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, frame) {
                    detectTransformGestures { _, pan, zoomChange, _ -> state.transform(pan, zoomChange, frame) }
                },
        ) {
            val scale = state.scale(frame)
            val drawn = Size(state.imageWidth * scale, state.imageHeight * scale)
            val topLeft = center + state.offset - Offset(drawn.width / 2, drawn.height / 2)
            drawImage(
                image,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
            )
            val frameRect = Rect(center - Offset(frame / 2, frame / 2), Size(frame, frame))
            val veil = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRect(frameRect)
            }
            drawPath(veil, Color.Black.copy(alpha = VEIL_ALPHA))
            val line = with(density) { 2.dp.toPx() }
            drawRect(Color.White, frameRect.topLeft, frameRect.size, style = Stroke(line))
            drawCircle(
                Color.White.copy(alpha = GUIDE_ALPHA),
                radius = frame * HEAD_SHARE,
                center = center,
                style = Stroke(line / 2),
            )
        }
    }
}

private const val VEIL_ALPHA = 0.55f
private const val GUIDE_ALPHA = 0.7f

/** The guide circle's radius, in frame widths: a head filling the square. */
private const val HEAD_SHARE = 0.42f
