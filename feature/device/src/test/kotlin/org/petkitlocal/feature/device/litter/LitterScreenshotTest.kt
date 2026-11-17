// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.core.ui.LiveCardState
import org.petkitlocal.core.ui.PreviewImages
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The litter box page in light/dark x EN/FR, the busiest state also at 130 % font scale: a Pura
 * Max with its day, a Purobot Ultra in trouble with its live card's still, its actions unfolded and
 * its recent clips, and a hotspot's explanation. The golden images are in src/test/screenshots.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width, tall enough for the whole page. SDK 36: Robolectric's SDK 37 graphics capture
// some images blank (ComponentScreenshotTest).
@Config(qualifiers = "w360dp-h1700dp-xhdpi", sdk = [36])
class LitterScreenshotTest(private val variant: ScreenshotVariant) {
    /** Mochi's photo is drawn as a placeholder: the capture fetches nothing. */
    @Test
    fun content() = capture("litter_content") { PreviewImages { Screen(LitterFixtures.content) } }

    /** The still and the clips' thumbnails are drawn as placeholders: the capture fetches nothing. */
    @Test
    fun trouble() {
        if (variant.fontScale == 1f) {
            capture("litter_trouble") {
                PreviewImages {
                    Screen(
                        LitterFixtures.trouble,
                        expanded = true,
                        clips = DeviceFixtures.recentClips,
                        live = DeviceFixtures.liveCard,
                    )
                }
            }
        }
    }

    /** A Pura Max with its Pura Air's N60, and that pill's dialog: EN light and FR dark. */
    @Test
    fun puraAir() {
        if (variant.toString() !in setOf("light_en_100", "dark_fr_100")) return
        capture("litter_pura_air") { PreviewImages { Screen(LitterFixtures.withPuraAir) } }
        val hotspot = LitterFixtures.withPuraAir.page!!.hotspots.first { it.part == DevicePart.PuraAirSpray }
        capture("litter_pura_air_hotspot") {
            Box(Modifier.background(PkTheme.colors.background).padding(16.dp)) {
                HotspotDialogContent(
                    hotspot,
                    sending = emptySet(),
                    onAction = {},
                    onDismiss = {},
                    accessory = LitterFixtures.puraAir,
                )
            }
        }
    }

    @Test
    fun hotspot() {
        if (variant.fontScale != 1f) return
        val hotspot = LitterFixtures.page().hotspots.first { it.part == DevicePart.Deodorizer }
        capture("litter_hotspot") {
            Box(Modifier.background(PkTheme.colors.background).padding(16.dp)) {
                HotspotDialogContent(hotspot, sending = emptySet(), onAction = {}, onDismiss = {})
            }
        }
    }

    @Composable
    private fun Screen(
        state: LitterUiState,
        expanded: Boolean = false,
        clips: RecentClipsUi? = null,
        live: LiveCardState = LiveCardState(),
    ) = LitterScreen(
        state,
        DeviceNavigation(),
        LitterCallbacks(),
        initiallyExpanded = expanded,
        clips = clips,
        live = live,
    )

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
