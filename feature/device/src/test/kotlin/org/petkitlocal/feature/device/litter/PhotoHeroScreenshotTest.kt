// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.devicepage.PartCondition
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The photo heroes (issue #69, layout A) on their page's tint, one model per family in light/dark
 * x EN/FR x 100/130 %: a Purobot Max Pro 2 whose N60 its photo does not place (its pill under
 * the photo), a YumShare Dual-Hopper 2 with an empty hopper, an EverSweet Max 2; and a model
 * without a photo, at 100 %. The golden images are in src/test/screenshots. SDK 36: Robolectric's
 * SDK 37 graphics capture some images blank.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h560dp-xhdpi", sdk = [36])
class PhotoHeroScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun litter() = capture("photo_hero_litter") {
        Hero(
            PkTheme.colors.heroLitter,
            DevicePhoto.PurobotMaxPro2,
            listOf(
                hotspot(DevicePart.Litter, PartReading.Percent(85)),
                hotspot(DevicePart.WasteBin, PartReading.Condition(PartCondition.Ok)),
                hotspot(DevicePart.Deodorizer, PartReading.DaysLeft(12)),
                hotspot(DevicePart.Spray, PartReading.DaysLeft(2), attention = true),
            ),
        )
    }

    @Test
    fun feeder() = capture("photo_hero_feeder") {
        Hero(
            PkTheme.colors.heroFeeder,
            DevicePhoto.YumShareDualHopper2,
            listOf(
                hotspot(DevicePart.Hopper1, PartReading.Option("Has food")),
                hotspot(DevicePart.Hopper2, PartReading.Option("Empty"), attention = true),
                hotspot(DevicePart.Desiccant, PartReading.DaysLeft(18)),
            ),
        )
    }

    @Test
    fun fountain() = capture("photo_hero_fountain") {
        Hero(
            PkTheme.colors.heroFountain,
            DevicePhoto.EverSweetMax2,
            listOf(
                hotspot(DevicePart.Water, PartReading.Condition(PartCondition.Ok)),
                hotspot(DevicePart.Filter, PartReading.Percent(30)),
                hotspot(DevicePart.Pump, PartReading.Condition(PartCondition.Running)),
                hotspot(DevicePart.Battery, PartReading.Percent(92)),
            ),
        )
    }

    /** A generation the app has no photo of (a Pura Max 3) keeps our drawing and its pills. */
    @Test
    fun noPhoto() {
        if (variant.fontScale != 1f) return
        capture("photo_hero_none") {
            Hero(
                PkTheme.colors.heroLitter,
                DevicePhoto.of("t4", generation = 3),
                listOf(
                    hotspot(DevicePart.Litter, PartReading.Percent(85)),
                    hotspot(DevicePart.WasteBin, PartReading.Condition(PartCondition.Ok)),
                    hotspot(DevicePart.Deodorizer, PartReading.DaysLeft(12)),
                ),
            )
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { content() }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}

/**
 * Every model's photo hero with every part it has, for the owner's review of the positions: the
 * photo's own parts beside their dots, the others under it. FR, light and dark, at a density low
 * enough to keep one image for all of them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1080dp-h2400dp-mdpi", sdk = [36])
class PhotoHeroCatalogScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun catalog() {
        captureRoboImage(
            filePath = "src/test/screenshots/photo_hero_catalog_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme {
                Column(Modifier.fillMaxSize().background(PkTheme.colors.background)) {
                    CATALOG.chunked(3).forEach { row ->
                        Row {
                            row.forEach { (photo, parts) ->
                                Column(Modifier.width(360.dp)) {
                                    Text(
                                        photo.name,
                                        Modifier.padding(start = 16.dp, top = 8.dp),
                                        PkTheme.colors.onSurface,
                                    )
                                    Hero(tintOf(photo), photo, parts.map(::sample))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun tintOf(photo: DevicePhoto): Color = when (photo) {
        DevicePhoto.PuraX, DevicePhoto.PuraMax, DevicePhoto.PuraMax2, DevicePhoto.PurobotMaxPro2,
        DevicePhoto.PurobotUltra, DevicePhoto.PurobotCrystalDuo, DevicePhoto.PuraAir,
        -> PkTheme.colors.heroLitter

        DevicePhoto.FreshElementSolo, DevicePhoto.FreshElementGemini, DevicePhoto.YumShareSolo,
        DevicePhoto.YumShareSolo2, DevicePhoto.YumShareDualHopper, DevicePhoto.YumShareDualHopper2,
        -> PkTheme.colors.heroFeeder

        else -> PkTheme.colors.heroFountain
    }

    companion object {
        /** Each model with its parts (LitterParts, FountainParts, the feeders' hotspots). */
        private val CATALOG: List<Pair<DevicePhoto, List<DevicePart>>> = listOf(
            DevicePhoto.PuraX to listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer),
            DevicePhoto.PuraMax to
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.PuraAirSpray),
            DevicePhoto.PuraMax2 to
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.PuraAirSpray),
            DevicePhoto.PurobotMaxPro2 to
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.Spray),
            DevicePhoto.PurobotUltra to
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Spray, DevicePart.Bags),
            DevicePhoto.PurobotCrystalDuo to
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.Spray),
            DevicePhoto.FreshElementSolo to listOf(DevicePart.Food, DevicePart.Bowl, DevicePart.Desiccant),
            DevicePhoto.FreshElementGemini to
                listOf(DevicePart.Hopper1, DevicePart.Hopper2, DevicePart.Bowl, DevicePart.Desiccant),
            DevicePhoto.YumShareSolo to listOf(DevicePart.Food, DevicePart.Bowl, DevicePart.Desiccant),
            DevicePhoto.YumShareSolo2 to listOf(DevicePart.Food, DevicePart.Bowl, DevicePart.Desiccant),
            DevicePhoto.YumShareDualHopper to
                listOf(DevicePart.Hopper1, DevicePart.Hopper2, DevicePart.Bowl, DevicePart.Desiccant),
            DevicePhoto.YumShareDualHopper2 to
                listOf(DevicePart.Hopper1, DevicePart.Hopper2, DevicePart.Bowl, DevicePart.Desiccant),
            DevicePhoto.EverSweetUltraAi to
                listOf(DevicePart.Tray, DevicePart.CleanTank, DevicePart.WasteTank, DevicePart.Pump),
            DevicePhoto.EverSweet3Pro to listOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump),
            DevicePhoto.EverSweetSolo2 to listOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump),
            DevicePhoto.EverSweetMax2 to
                listOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump, DevicePart.Battery),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> =
            listOf(ScreenshotVariant(dark = false, locale = "fr"), ScreenshotVariant(dark = true, locale = "fr"))
    }
}

/** A hero on its page's tint fading into the background, as at the top of a device page. */
@Composable
private fun Hero(tint: Color, photo: DevicePhoto?, hotspots: List<Hotspot>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(Brush.verticalGradient(listOf(tint, PkTheme.colors.background)))
            .padding(16.dp),
    ) {
        DeviceHero(hotspots, anchors = ::litterAnchor, onHotspot = {}, photo = photo) {
            LitterBoxIllustration(Modifier.fillMaxSize())
        }
    }
}

private fun hotspot(part: DevicePart, reading: PartReading, attention: Boolean = false) =
    Hotspot(part, part.name, part.name, reading, attention, emptyList())

/** A plausible reading of [part], the N60s and an empty hopper in the warning style. */
private fun sample(part: DevicePart): Hotspot {
    val ok = PartReading.Condition(PartCondition.Ok)
    val readings: Map<DevicePart, PartReading> = mapOf(
        DevicePart.Litter to PartReading.Percent(88),
        DevicePart.Deodorizer to PartReading.DaysLeft(21),
        DevicePart.Spray to PartReading.DaysLeft(3),
        DevicePart.PuraAirSpray to PartReading.DaysLeft(3),
        DevicePart.Bags to PartReading.Count(14),
        DevicePart.Food to PartReading.Option("Has food"),
        DevicePart.Hopper1 to PartReading.Option("Has food"),
        DevicePart.Hopper2 to PartReading.Option("Empty"),
        DevicePart.Bowl to PartReading.Percent(40),
        DevicePart.Desiccant to PartReading.DaysLeft(18),
        DevicePart.Filter to PartReading.Percent(64),
        DevicePart.Pump to PartReading.Condition(PartCondition.Running),
        DevicePart.Battery to PartReading.Percent(92),
    )
    val attention = part in setOf(DevicePart.Spray, DevicePart.PuraAirSpray, DevicePart.Hopper2)
    return hotspot(part, readings[part] ?: ok, attention)
}
