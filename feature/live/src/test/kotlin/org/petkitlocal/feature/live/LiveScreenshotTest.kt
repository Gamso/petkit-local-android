// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live video screen in EN/FR (always dark, so light variants would only repeat the dark
 * ones), the playing state also at 130 % font scale: playing on the WebSocket, on HLS with a
 * snapshot saved, connecting, waiting to reconnect, stopped, full screen in landscape, Talk held,
 * a camera that cannot talk (and Talk pressed anyway), the microphone refused, and a device
 * without a camera. No player runs here: the picture is the black box around it. The
 * golden images are in src/test/screenshots. Inspection mode keeps the spinner still: animated, it
 * never lets the looper go idle and the capture hangs (it did, on CI).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone size. SDK 36: Robolectric's SDK 37 graphics capture some images blank.
@Config(qualifiers = "w360dp-h780dp-xhdpi", sdk = [36])
class LiveScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun playing() = capture("live_playing") { LiveScreen(LiveFixtures.playing, LiveCallbacks()) }

    @Test
    fun hls() = screen("live_hls") { LiveScreen(LiveFixtures.playingHls, LiveCallbacks()) }

    @Test
    fun connecting() = screen("live_connecting") { LiveScreen(LiveFixtures.connecting, LiveCallbacks()) }

    @Test
    fun waiting() = screen("live_waiting") { LiveScreen(LiveFixtures.waiting, LiveCallbacks()) }

    @Test
    fun stopped() = screen("live_stopped") { LiveScreen(LiveFixtures.stopped, LiveCallbacks()) }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land-xhdpi", sdk = [36])
    fun fullscreen() = screen("live_fullscreen") { LiveScreen(LiveFixtures.fullscreen, LiveCallbacks()) }

    @Test
    fun talking() = screen("live_talking") { LiveScreen(LiveFixtures.talking, LiveCallbacks()) }

    @Test
    fun talkNeedsPatcher() = screen("live_talk_needs_patcher") {
        LiveScreen(LiveFixtures.talkNeedsPatcher, LiveCallbacks())
    }

    @Test
    fun talkNeedsPatcherPressed() =
        screen("live_talk_needs_patcher_pressed") { LiveScreen(LiveFixtures.talkNeedsPatcherPressed, LiveCallbacks()) }

    @Test
    fun microphoneDenied() = screen("live_talk_microphone_denied") {
        LiveScreen(LiveFixtures.microphoneDenied, LiveCallbacks())
    }

    @Test
    fun notACamera() = screen("live_not_a_camera") { LiveScreen(LiveFixtures.notACamera, LiveCallbacks()) }

    /** The other states at 100 % only: the playing one carries the 130 % check. */
    private fun screen(name: String, content: @Composable () -> Unit) {
        if (variant.fontScale == 1f) capture(name, content)
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all.filter { it.dark }
    }
}
