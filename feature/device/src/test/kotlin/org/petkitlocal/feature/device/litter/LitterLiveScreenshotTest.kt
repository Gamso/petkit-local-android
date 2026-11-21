// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.core.ui.PreviewImages
import org.petkitlocal.core.ui.live.LivePicture
import org.petkitlocal.core.ui.talk.TalkPhase
import org.petkitlocal.core.ui.talk.TalkUiState
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.LiveCardUi
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The top of a camera's page (a Purobot Ultra) with its live video playing in place, in each
 * state: opening over the cached still with a busy indicator, still opening after a while (a
 * D4SH's relay), playing (the picture is a black placeholder: no player runs here), and stopped
 * with its reason and retry on the video. Light/dark x EN/FR; the error, the longest text, also
 * at 130 %. The still is drawn as a placeholder: the capture fetches nothing. Inspection mode
 * keeps the spinner still: animated, it never lets the looper go idle (LiveScreenshotTest).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone size, the top of the page. SDK 36: Robolectric's SDK 37 graphics capture some images blank.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class LitterLiveScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun loading() = screen("litter_live_loading", session(LivePhase.Starting))

    @Test
    fun slowStart() = screen("litter_live_slow_start", session(LivePhase.Starting).copy(startingSlowly = true))

    @Test
    fun playing() = screen("litter_live_playing", session(LivePhase.Playing)) { modifier ->
        Box(modifier.background(LivePicture))
    }

    @Test
    fun error() {
        val stopped = LivePhase.Stopped(LiveFailure.Refused(DomainError.RateLimited(null)))
        capture("litter_live_error", session(stopped))
    }

    /** Talk held under the playing picture: the red dot and what is happening. */
    @Test
    fun talking() {
        if (variant.fontScale != 1f) return
        val talking = TalkUiState(TalkAvailability.Available, TalkPhase.Talking)
        capture("litter_live_talking", session(LivePhase.Playing), talk = talking) { modifier ->
            Box(modifier.background(LivePicture))
        }
    }

    /** Full screen in place, in landscape: the same card over the page, Talk at the bottom. */
    @Test
    @Config(qualifiers = "w780dp-h360dp-land-xhdpi", sdk = [36])
    fun fullscreen() {
        if (variant.fontScale != 1f) return
        capture(
            "litter_live_fullscreen",
            session(LivePhase.Playing),
            talk = TalkUiState(TalkAvailability.Available),
            fullscreen = true,
        ) { modifier -> Box(modifier.background(PlaceholderPicture)) }
    }

    private fun session(phase: LivePhase) = LiveSession(phase = phase, attempt = 1)

    /** At 100 % only: the error carries the 130 % check. */
    private fun screen(name: String, session: LiveSession, video: @Composable (Modifier) -> Unit = {}) {
        if (variant.fontScale == 1f) capture(name, session, video = video)
    }

    private fun capture(
        name: String,
        session: LiveSession,
        talk: TalkUiState = TalkUiState(TalkAvailability.Available),
        fullscreen: Boolean = false,
        video: @Composable (Modifier) -> Unit = {},
    ) {
        val state = DeviceFixtures.liveCard.copy(session = session, talk = talk, fullscreen = fullscreen)
        val live = LiveCardUi(state = state, video = video)
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PkTheme {
                    PreviewImages {
                        LitterScreen(LitterFixtures.trouble, DeviceNavigation(), LitterCallbacks(), live = live)
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}

/** A grey stand-in for the camera's picture, so the full screen's letterbox shows. */
private val PlaceholderPicture = Color(0xFF3A3A3A)
