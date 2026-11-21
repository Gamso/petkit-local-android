package org.petkitlocal.feature.device.litter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.FountainParts
import org.petkitlocal.core.domain.devicepage.LitterParts

class PhotoPillLayoutTest {
    private val layout = PhotoPillLayout(frame = FRAME, gap = GAP, margin = MARGIN, spacing = SPACING)

    private fun pill(x: Int, y: Int, side: PillSide, width: Int = 100, height: Int = 50) =
        PillRequest(width, height, PixelPoint(x, y), side)

    @Test
    fun `a pill sits beside its point, centred on it, on its side`() {
        val placed = layout.place(listOf(pill(400, 300, PillSide.Left), pill(400, 600, PillSide.Right)))
        assertEquals(PixelPoint(400 - GAP - 100, 275), placed.positions[0])
        assertEquals(PixelPoint(400 + GAP, 575), placed.positions[1])
        assertEquals(FRAME, placed.height)
    }

    @Test
    fun `a pill that would stick out goes to the other side of its point`() {
        val placed = layout.place(listOf(pill(60, 300, PillSide.Left), pill(760, 600, PillSide.Right)))
        assertEquals(60 + GAP, placed.positions[0].x)
        assertEquals(760 - GAP - 100, placed.positions[1].x)
    }

    @Test
    fun `a pill that fits on neither side is slid inside the frame`() {
        val wide = 700
        val placed = layout.place(listOf(pill(400, 300, PillSide.Left, width = wide), pill(400, 10, PillSide.Right)))
        assertEquals(MARGIN, placed.positions[0].x)
        // Its top too: a point near the top edge does not lift the pill out of the frame.
        assertEquals(MARGIN, placed.positions[1].y)
    }

    @Test
    fun `every pill stays inside the frame`() {
        val pills = listOf(
            pill(0, 0, PillSide.Left),
            pill(FRAME, 0, PillSide.Right),
            pill(0, FRAME, PillSide.Right),
            pill(FRAME, FRAME, PillSide.Left),
            pill(FRAME / 2, FRAME / 2, PillSide.Left, width = FRAME - 2 * MARGIN),
        )
        val placed = layout.place(pills)
        placed.positions.forEachIndexed { i, at ->
            assertTrue(at.x >= MARGIN && at.x + pills[i].width <= FRAME - MARGIN) { "pill $i at $at" }
            assertTrue(at.y >= MARGIN && at.y + pills[i].height <= placed.height - MARGIN) { "pill $i at $at" }
        }
    }

    @Test
    fun `overlapping pills are pushed apart, the higher one keeping its place`() {
        val pills =
            listOf(pill(400, 320, PillSide.Right), pill(400, 300, PillSide.Right), pill(410, 310, PillSide.Right))
        val placed = layout.place(pills)
        assertEquals(275, placed.positions[1].y)
        assertNoOverlap(pills, placed)
        // Pushed down in the order of their points.
        assertEquals(275 + 50 + SPACING, placed.positions[2].y)
        assertEquals(275 + 2 * (50 + SPACING), placed.positions[0].y)
    }

    @Test
    fun `pills on either side of a point do not push each other`() {
        val placed = layout.place(listOf(pill(400, 300, PillSide.Left), pill(400, 300, PillSide.Right)))
        assertEquals(placed.positions[0].y, placed.positions[1].y)
    }

    @Test
    fun `the hero grows for pills pushed past the frame`() {
        val pills = (0 until 6).map { pill(400, FRAME - 60, PillSide.Right, height = 60) }
        val placed = layout.place(pills)
        assertNoOverlap(pills, placed)
        val lowest = placed.positions.maxOf { it.y } + 60
        assertEquals(lowest + MARGIN, placed.height)
    }

    @Test
    fun `a pill with no point goes under the photo, in rows`() {
        val pills = listOf(
            pill(400, 300, PillSide.Left),
            PillRequest(300, 50, null, PillSide.Right),
            PillRequest(300, 50, null, PillSide.Right),
            PillRequest(300, 50, null, PillSide.Right),
        )
        val placed = layout.place(pills)
        val under = layout.photoOffset + layout.photoSize + SPACING
        assertEquals(PixelPoint(MARGIN, under), placed.positions[1])
        assertEquals(PixelPoint(MARGIN + 300 + SPACING, under), placed.positions[2])
        assertEquals(PixelPoint(MARGIN, under + 50 + SPACING), placed.positions[3])
        assertEquals(under + 2 * 50 + SPACING + MARGIN, placed.height)
        assertNoOverlap(pills, placed)
    }

    @Test
    fun `a spot is placed as on the reviewed renders`() {
        // The renders put the photo's box at 10 % + 80 % of x and 6 % + 88 % of y.
        assertEquals(PixelPoint(80 + 640 / 2, 48 + 704 / 2), layout.pointOf(PhotoSpot(50f, 50f, PillSide.Left)))
        assertEquals(PixelPoint(80, 48), layout.pointOf(PhotoSpot(0f, 0f, PillSide.Left)))
        assertEquals(PixelPoint(720, 752), layout.pointOf(PhotoSpot(100f, 100f, PillSide.Left)))
        assertEquals(80, layout.photoOffset)
        assertEquals(640, layout.photoSize)
    }

    /** Every placed part is one the model has, and on its photo. */
    @Test
    fun `the photos place only parts their model has`() {
        val types = listOf(
            "t3" to null,
            "t4" to 1,
            "t4" to 2,
            "t5" to null,
            "t6" to null,
            "t7" to null,
            "w7h" to null,
            "w5" to null,
            "ctw2" to null,
            "ctw3" to 2,
        )
        types.forEach { (type, generation) ->
            val photo = DevicePhoto.of(type, generation)!!
            val parts = (LitterParts.of(type) ?: FountainParts.BY_TYPE.getValue(type)) + DevicePart.PuraAirSpray
            DevicePart.entries.filter { PhotoSpots.of(photo, it) != null }.forEach { part ->
                assertTrue(part in parts) { "$type places $part" }
            }
        }
        DevicePhoto.entries.forEach { photo ->
            DevicePart.entries.mapNotNull { PhotoSpots.of(photo, it) }.forEach { spot ->
                assertTrue(spot.x in 0f..100f && spot.y in 0f..100f) { "$photo $spot" }
            }
        }
        // The Pura Air's photo is no page's hero: it places nothing.
        assertFalse(PhotoSpots.placesParts(DevicePhoto.PuraAir))
        assertTrue(DevicePhoto.entries.filter { it != DevicePhoto.PuraAir }.all(PhotoSpots::placesParts))
    }

    private fun assertNoOverlap(pills: List<PillRequest>, placed: PillPlacement) {
        pills.indices.forEach { i ->
            (0 until i).forEach { j ->
                val a = placed.positions[i]
                val b = placed.positions[j]
                val apart = a.x + pills[i].width <= b.x || b.x + pills[j].width <= a.x ||
                    a.y + pills[i].height <= b.y || b.y + pills[j].height <= a.y
                assertTrue(apart) { "pills $i at $a and $j at $b overlap" }
            }
        }
    }

    private companion object {
        const val FRAME = 800
        const val GAP = 16
        const val MARGIN = 8
        const val SPACING = 8
    }
}
