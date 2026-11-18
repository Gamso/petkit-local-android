package org.petkitlocal.feature.device.litter

import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.domain.devicepage.DevicePart

/**
 * Where a part is on a model's photo, in percent of the photo padded to a square ([x] from the
 * left, [y] from the top), and which side of that point its pill goes.
 */
data class PhotoSpot(val x: Float, val y: Float, val side: PillSide)

/**
 * The parts on each model's photo, as the owner placed them on the reviewed renders (issue #69's
 * table, 2026-11-17). Only the parts the app has a pill for are here: the renders' camera pills
 * name no part the server reports. A part a model has but its photo does not place (the N60 of a
 * Purobot, a consumable added later) still gets its pill, under the photo and without a dot
 * ([PhotoHero]): a dot would claim a place on the product nobody checked.
 *
 * The Pura Max 2 has no mat pill (the mat is only a reminder to clean it); its N60 is the liquid
 * on the left of the box, which a paired Pura Air provides ([DevicePart.PuraAirSpray]).
 */
@Suppress("MagicNumber") // Positions on the photos.
object PhotoSpots {
    private val L = PillSide.Left
    private val R = PillSide.Right

    private val SPOTS: Map<DevicePhoto, Map<DevicePart, PhotoSpot>> = mapOf(
        DevicePhoto.PuraX to mapOf(
            DevicePart.Litter to PhotoSpot(50f, 42f, L),
            DevicePart.WasteBin to PhotoSpot(50f, 87f, L),
            DevicePart.Deodorizer to PhotoSpot(70f, 15f, R),
        ),
        DevicePhoto.PuraMax to mapOf(
            DevicePart.Litter to PhotoSpot(37f, 57f, L),
            DevicePart.Deodorizer to PhotoSpot(80f, 64f, R),
            DevicePart.WasteBin to PhotoSpot(82f, 80f, R),
        ),
        DevicePhoto.PuraMax2 to mapOf(
            DevicePart.PuraAirSpray to PhotoSpot(6f, 45f, L),
            DevicePart.Litter to PhotoSpot(30f, 55f, L),
            DevicePart.Deodorizer to PhotoSpot(82f, 66f, R),
            DevicePart.WasteBin to PhotoSpot(88f, 82f, R),
        ),
        DevicePhoto.PurobotMaxPro2 to mapOf(
            DevicePart.Litter to PhotoSpot(43f, 50f, L),
            DevicePart.Deodorizer to PhotoSpot(80f, 58f, R),
            DevicePart.WasteBin to PhotoSpot(80f, 72f, R),
        ),
        DevicePhoto.PurobotUltra to mapOf(
            DevicePart.Litter to PhotoSpot(42f, 40f, L),
            DevicePart.WasteBin to PhotoSpot(45f, 88f, L),
            DevicePart.Bags to PhotoSpot(72f, 80f, R),
        ),
        DevicePhoto.PurobotCrystalDuo to mapOf(
            DevicePart.Litter to PhotoSpot(30f, 60f, L),
            DevicePart.WasteBin to PhotoSpot(70f, 43f, R),
        ),
        DevicePhoto.FreshElementSolo to mapOf(
            DevicePart.Desiccant to PhotoSpot(45f, 5f, R),
            DevicePart.Food to PhotoSpot(45f, 30f, L),
            DevicePart.Hopper1 to PhotoSpot(45f, 30f, L),
            DevicePart.Bowl to PhotoSpot(35f, 85f, L),
        ),
        DevicePhoto.FreshElementGemini to mapOf(
            DevicePart.Hopper1 to PhotoSpot(35f, 27f, L),
            DevicePart.Desiccant to PhotoSpot(50f, 12f, R),
            DevicePart.Hopper2 to PhotoSpot(52f, 27f, R),
            DevicePart.Bowl to PhotoSpot(48f, 78f, L),
        ),
        DevicePhoto.YumShareSolo to mapOf(
            DevicePart.Bowl to PhotoSpot(30f, 80f, L),
            DevicePart.Desiccant to PhotoSpot(60f, 6f, R),
            DevicePart.Food to PhotoSpot(60f, 27f, R),
            DevicePart.Hopper1 to PhotoSpot(60f, 27f, R),
        ),
        DevicePhoto.YumShareSolo2 to mapOf(
            DevicePart.Bowl to PhotoSpot(40f, 68f, L),
            DevicePart.Desiccant to PhotoSpot(60f, 2f, R),
            DevicePart.Food to PhotoSpot(62f, 25f, R),
            DevicePart.Hopper1 to PhotoSpot(62f, 25f, R),
        ),
        DevicePhoto.YumShareDualHopper to mapOf(
            DevicePart.Hopper1 to PhotoSpot(33f, 27f, L),
            DevicePart.Bowl to PhotoSpot(35f, 73f, L),
            DevicePart.Desiccant to PhotoSpot(53f, 13f, R),
            DevicePart.Hopper2 to PhotoSpot(44f, 27f, R),
        ),
        DevicePhoto.YumShareDualHopper2 to mapOf(
            DevicePart.Hopper1 to PhotoSpot(32f, 15f, L),
            DevicePart.Bowl to PhotoSpot(30f, 82f, L),
            DevicePart.Desiccant to PhotoSpot(45f, 2f, R),
            DevicePart.Hopper2 to PhotoSpot(46f, 20f, R),
        ),
        DevicePhoto.EverSweetUltraAi to mapOf(
            DevicePart.Tray to PhotoSpot(30f, 55f, L),
            DevicePart.CleanTank to PhotoSpot(80f, 10f, R),
            DevicePart.WasteTank to PhotoSpot(75f, 80f, R),
        ),
        DevicePhoto.EverSweet3Pro to mapOf(
            DevicePart.Water to PhotoSpot(48f, 25f, L),
            DevicePart.Pump to PhotoSpot(48f, 62f, L),
            DevicePart.Filter to PhotoSpot(52f, 32f, R),
        ),
        DevicePhoto.EverSweetSolo2 to mapOf(
            DevicePart.Water to PhotoSpot(50f, 10f, L),
            DevicePart.Filter to PhotoSpot(62f, 45f, R),
        ),
        DevicePhoto.EverSweetMax2 to mapOf(
            DevicePart.Battery to PhotoSpot(22f, 14f, L),
            DevicePart.Pump to PhotoSpot(38f, 27f, L),
            DevicePart.Filter to PhotoSpot(46f, 33f, R),
            DevicePart.Water to PhotoSpot(66f, 34f, R),
        ),
    )

    /** Where [part] is on [photo], or null when the photo does not place it. */
    fun of(photo: DevicePhoto, part: DevicePart): PhotoSpot? = SPOTS[photo]?.get(part)

    /** Whether [photo] places any part: one that places none (a Pura Air's) is no page's hero. */
    fun placesParts(photo: DevicePhoto): Boolean = SPOTS[photo].orEmpty().isNotEmpty()
}
