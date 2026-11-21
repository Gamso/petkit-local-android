package org.petkitlocal.core.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.video.LatestStill
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the live card at the top of a camera's page, playing in place. */
@RunWith(RobolectricTestRunner::class)
class LiveCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 15)
    private val calls = mutableListOf<String>()
    private val callbacks = LiveCardCallbacks(
        onRetry = { calls += "retry" },
        onToggleMute = { calls += "mute" },
        onFullscreen = { calls += "fullscreen" },
    )

    private fun show(
        still: LatestStill? = still(today, 4, 2),
        session: LiveSession = LiveSession(),
        muted: Boolean = true,
    ) {
        compose.setContent {
            PreviewImages {
                PkTheme {
                    LiveCard(
                        deviceName = "Purobot Ultra",
                        state = LiveCardState(still, zone, today, session, muted),
                        callbacks = callbacks,
                    )
                }
            }
        }
    }

    private fun still(day: LocalDate, hour: Int, minute: Int) = LatestStill(
        "https://pets.example/app/v1/media/thumb/a/snap.jpg",
        ZonedDateTime.of(day.atTime(hour, minute), zone).toInstant(),
    )

    private fun session(phase: LivePhase, transport: LiveTransport = LiveTransport.WebSocket) =
        LiveSession(transport = transport, phase = phase, attempt = 1)

    private val card get() = compose.onNodeWithTag(LiveCardTestTags.CARD)

    @Test
    fun `the card names the device and when its still was taken, and tapping the video does nothing`() {
        show()

        // The locale's own time format puts a narrow space before AM.
        card.assertContentDescriptionEquals("Live video of Purobot Ultra. Still from 4:02 AM")
            .assert(hasClickAction().not())
            .performClick()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a still from another day says which`() {
        show(still(today.minusDays(1), 22, 40))
        card.assertContentDescriptionEquals("Live video of Purobot Ultra. Still from yesterday, 10:40 PM")
    }

    @Test
    fun `a still from an older day says the day without its year`() {
        show(still(today.minusDays(3), 7, 5))
        card.assertContentDescriptionEquals("Live video of Purobot Ultra. Still from Nov 12, 7:05 AM")
    }

    @Test
    fun `without a still, the card says no time`() {
        show(still = null)
        card.assertContentDescriptionEquals("Live video of Purobot Ultra")
    }

    @Test
    fun `until the first frame, the still stays with a busy indicator and says it is connecting`() {
        show(session = session(LivePhase.Starting))

        compose.onNodeWithTag(LiveCardTestTags.STILL).assertExists()
        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        compose.onNodeWithTag(LiveVideoTestTags.BADGE).assertDoesNotExist()
        compose.onNodeWithText("Watch again").assertDoesNotExist()
    }

    @Test
    fun `a slow first open shows progress, not an error`() {
        show(session = session(LivePhase.Starting).copy(startingSlowly = true))

        compose.onNodeWithText("Waking the camera up… This can take up to 30 seconds.").assertIsDisplayed()
        compose.onNodeWithText("Watch again").assertDoesNotExist()
    }

    @Test
    fun `playing, the still gives way to the picture, with the live badge`() {
        show(session = session(LivePhase.Playing))

        compose.onNodeWithTag(LiveCardTestTags.STILL).assertDoesNotExist()
        compose.onNodeWithTag(LiveVideoTestTags.BADGE).assertIsDisplayed()
        compose.onNodeWithTag(LiveVideoTestTags.STATUS).assertDoesNotExist()
        card.assertContentDescriptionEquals("Live video of Purobot Ultra")
    }

    @Test
    fun `on the fallback it says HLS`() {
        show(session = session(LivePhase.Playing, LiveTransport.Hls))
        compose.onNodeWithText("HLS").assertIsDisplayed()
    }

    @Test
    fun `waiting to start again says why, with the live screen's words`() {
        show(session = session(LivePhase.Waiting(LiveFailure.Network(), LiveSession.RETRY_DELAY)))
        compose.onNodeWithText("Reconnecting… The connection to the server broke.").assertIsDisplayed()
    }

    @Test
    fun `stopped, it says why on the video and the user starts it again`() {
        show(session = session(LivePhase.Stopped(LiveFailure.Refused(DomainError.RateLimited(null)))))

        compose.onNodeWithText("Too many people are watching. Try again in a moment.").assertIsDisplayed()
        compose.onNodeWithText("Watch again").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `sound and full screen are on the video, muted at first, and always pressable`() {
        show(session = session(LivePhase.Stopped(LiveFailure.StreamEnded)))

        compose.onNodeWithContentDescription("Turn sound on").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Full screen").assertIsEnabled().performClick()
        assertEquals(listOf("mute", "fullscreen"), calls)
    }

    @Test
    fun `with sound on, the button mutes it`() {
        show(session = session(LivePhase.Playing), muted = false)
        compose.onNodeWithContentDescription("Mute").performClick()
        assertEquals(listOf("mute"), calls)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `in French, it follows the locale`() {
        show(still(today, 17, 0), session = session(LivePhase.Starting).copy(startingSlowly = true))
        card.assertContentDescriptionEquals("Vidéo en direct de Purobot Ultra. Image de 17:00")
        compose.onNodeWithText("Réveil de la caméra… Cela peut prendre jusqu’à 30 secondes.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Activer le son").assertExists()
        compose.onNodeWithContentDescription("Plein écran").assertExists()
    }
}
