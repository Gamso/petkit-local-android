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
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.core.ui.PreviewImages
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.LiveCardUi
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.petkitlocal.feature.device.litter.HotspotDialogContent
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fountain page in light/dark x EN/FR, each with its mode selector at the bottom: the W7H on
 * mains with its drinks (the busiest state), a Bluetooth W5 with its relay online, a CTW3 in
 * trouble with its relay offline and the server's refusal (neither has More: their one button is
 * on the filter's pill), the CTW3 unplugged, its battery mode filled and a mode refused with the
 * server's `on_battery`, all four also at 130 % font scale; the W7H whose drinks came without
 * durations, the W5's filter explanation and the CTW3's battery mode window. The golden images are
 * in src/test/screenshots.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width, tall enough for the whole page. SDK 36: Robolectric's SDK 37 graphics capture
// some images blank (ComponentScreenshotTest).
@Config(qualifiers = "w360dp-h1700dp-xhdpi", sdk = [36])
class FountainScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun content() = capture("fountain_content") {
        PreviewImages {
            Screen(FountainFixtures.content, DeviceFixtures.recentClips, LiveCardUi(DeviceFixtures.liveCard))
        }
    }

    @Test
    fun bluetooth() = capture("fountain_bluetooth") { Screen(FountainFixtures.bluetooth) }

    @Test
    fun trouble() = capture("fountain_trouble") { Screen(FountainFixtures.trouble) }

    @Test
    fun battery() = capture("fountain_battery") { Screen(FountainFixtures.battery) }

    @Test
    fun batteryMode() {
        if (variant.fontScale != 1f) return
        val page = FountainFixtures.battery.page!!
        capture("fountain_battery_mode") {
            Box(Modifier.background(PkTheme.colors.background).padding(16.dp)) {
                FountainBatteryModeContent(page.device, page.modeBar!!.battery!!, Household.zone, {}, {})
            }
        }
    }

    @Test
    fun untimed() {
        if (variant.fontScale == 1f) capture("fountain_untimed") { Screen(FountainFixtures.untimed) }
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
    private fun Screen(state: FountainUiState, clips: RecentClipsUi? = null, live: LiveCardUi = LiveCardUi()) =
        FountainScreen(state, DeviceNavigation(), FountainCallbacks(), clips = clips, live = live)

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
