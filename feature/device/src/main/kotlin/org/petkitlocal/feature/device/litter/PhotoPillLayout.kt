package org.petkitlocal.feature.device.litter

import kotlin.math.roundToInt

/** A point in pixels. */
data class PixelPoint(val x: Int, val y: Int)

/** A pill to place: its measured size, the point it names (null when none) and its preferred side. */
data class PillRequest(val width: Int, val height: Int, val point: PixelPoint?, val side: PillSide)

/** Where each pill goes (its top-left corner, in request order) and how tall the hero is. */
data class PillPlacement(val positions: List<PixelPoint>, val height: Int)

/**
 * The geometry of a photo hero (layout A of issue #69), kept free of Compose so it is unit-tested.
 * The hero is a [frame]-pixel square holding the photo with a 10 % margin on every side. A pill
 * goes right beside its point, [gap] away on its side, centred on it; it changes side when it
 * would stick out of the frame and fits on the other one, else it is slid back inside, [margin]
 * from the edge. Pills that overlap are pushed down, [spacing] apart, the higher one keeping its
 * place. A pill with no point goes under the photo, in rows. The hero grows only for what the
 * frame cannot hold.
 */
class PhotoPillLayout(private val frame: Int, private val gap: Int, private val margin: Int, private val spacing: Int) {
    /** The photo's square inside the frame: its left and top edge, and its side. */
    val photoOffset: Int get() = (frame * PHOTO_INSET).roundToInt()
    val photoSize: Int get() = frame - 2 * photoOffset

    /** Where [spot] is in the frame ([onPhoto]). */
    fun pointOf(spot: PhotoSpot): PixelPoint {
        val (x, y) = onPhoto(spot)
        return PixelPoint(photoOffset + (photoSize * x).roundToInt(), photoOffset + (photoSize * y).roundToInt())
    }

    fun place(pills: List<PillRequest>): PillPlacement {
        val boxes = arrayOfNulls<Box>(pills.size)
        // Highest point first: a pill pushed down never displaces one placed above it.
        pills.indices.filter { pills[it].point != null }.sortedBy { pills[it].point!!.y }.forEach { i ->
            var box = beside(pills[i])
            do {
                val blocker = boxes.filterNotNull().firstOrNull { it.overlaps(box, spacing) }
                if (blocker != null) box = box.copy(top = blocker.bottom + spacing)
            } while (blocker != null)
            boxes[i] = box
        }
        var bottom = maxOf(frame, (boxes.filterNotNull().maxOfOrNull { it.bottom + margin } ?: 0))
        // The pills that name no point: rows under the photo and whatever came down past it.
        var x = margin
        var top = maxOf(photoOffset + photoSize, boxes.filterNotNull().maxOfOrNull { it.bottom } ?: 0) + spacing
        var rowBottom = top
        pills.indices.filter { pills[it].point == null }.forEach { i ->
            val pill = pills[i]
            if (x > margin && x + pill.width > frame - margin) {
                x = margin
                top = rowBottom + spacing
            }
            boxes[i] = Box(x, top, pill.width, pill.height)
            x += pill.width + spacing
            rowBottom = maxOf(rowBottom, top + pill.height)
            bottom = maxOf(bottom, rowBottom + margin)
        }
        return PillPlacement(boxes.map { PixelPoint(it!!.left, it.top) }, bottom)
    }

    /** [pill] beside its point, on the side it prefers if it fits there, inside the frame anyway. */
    private fun beside(pill: PillRequest): Box {
        val point = pill.point!!
        val right = point.x + gap
        val left = point.x - gap - pill.width
        val fitsRight = right + pill.width <= frame - margin
        val fitsLeft = left >= margin
        val x = when {
            pill.side == PillSide.Left && (fitsLeft || !fitsRight) -> left
            pill.side == PillSide.Right && (fitsRight || !fitsLeft) -> right
            pill.side == PillSide.Left -> right
            else -> left
        }.coerceAtMost(frame - margin - pill.width).coerceAtLeast(margin)
        val y = (point.y - pill.height / 2).coerceAtLeast(margin)
        return Box(x, y, pill.width, pill.height)
    }

    private data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height

        fun overlaps(other: Box, spacing: Int): Boolean = left < other.right + spacing &&
            other.left < right + spacing &&
            top < other.bottom + spacing &&
            other.top < bottom + spacing
    }

    companion object {
        /**
         * Where [spot] is on the photo's square, as fractions of its side (a point may lie a little
         * outside it). The mapping is the one the positions were placed with on the reviewed
         * renders: the photo stretched over a box 80 % wide and 88 % tall from (10 %, 6 %) of the
         * frame, where the square photo itself fills (10 %, 10 %) to (90 %, 90 %).
         */
        fun onPhoto(spot: PhotoSpot): Pair<Float, Float> = Pair(
            (BOX_LEFT + BOX_WIDTH * spot.x / PERCENT - PHOTO_INSET) / (1 - 2 * PHOTO_INSET),
            (BOX_TOP + BOX_HEIGHT * spot.y / PERCENT - PHOTO_INSET) / (1 - 2 * PHOTO_INSET),
        )

        private const val PERCENT = 100f
        private const val PHOTO_INSET = 0.1f
        private const val BOX_LEFT = 0.1f
        private const val BOX_TOP = 0.06f
        private const val BOX_WIDTH = 0.8f
        private const val BOX_HEIGHT = 0.88f
    }
}
