// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.fountain

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
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.petkitlocal.feature.device.litter.HotspotDialogContent
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fountain page in light/dark x EN/FR: the W7H with its drinks (the busiest state, also at
 * 130 % font scale), a Bluetooth W5 with its relay, a CTW3 in trouble with the server's refusal
 * (neither has an action sheet: their one button is on the filter's pill), and the W5's filter
 * explanation. The golden images are in src/test/screenshots.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width, tall enough for the whole page. SDK 36: Robolectric's SDK 37 graphics capture
// some images blank (ComponentScreenshotTest).
@Config(qualifiers = "w360dp-h1700dp-xhdpi", sdk = [36])
class FountainScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun content() = capture("fountain_content") { Screen(FountainFixtures.content, DeviceFixtures.recentClips) }

    @Test
    fun bluetooth() {
        if (variant.fontScale == 1f) capture("fountain_bluetooth") { Screen(FountainFixtures.bluetooth) }
    }

    @Test
    fun trouble() {
        if (variant.fontScale == 1f) capture("fountain_trouble") { Screen(FountainFixtures.trouble) }
    }

    @Test
    fun hotspot() {
        if (variant.fontScale != 1f) return
        val hotspot = FountainFixtures.bluetooth.page!!.hotspots.first { it.part == DevicePart.Filter }
        capture("fountain_hotspot") {
            Box(Modifier.background(PkTheme.colors.background).padding(16.dp)) {
                HotspotDialogContent(hotspot, sending = emptySet(), onAction = {}, onDismiss = {})
            }
        }
    }

    @Composable
    private fun Screen(state: FountainUiState, clips: RecentClipsUi? = null) =
        FountainScreen(state, DeviceNavigation(), FountainCallbacks(), clips = clips)

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
