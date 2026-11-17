package org.petkitlocal.feature.live

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.ui.RecentClipsTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless live video screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp")
class LiveScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val callbacks = LiveCallbacks(
        onBack = { calls += "back" },
        onRetry = { calls += "retry" },
        onToggleMute = { calls += "mute" },
        onFullscreen = { calls += "fullscreen:$it" },
        onSnapshot = { calls += "snapshot" },
        onOpenClip = { calls += "clip:${it.kind}" },
        onSeeAllClips = { calls += "all-clips" },
        onRetryClips = { calls += "retry-clips" },
    )

    private fun show(state: LiveUiState) {
        compose.setContent { LiveScreen(state, callbacks) }
    }

    @Test
    fun `playing, the badge says how far behind it is, and the controls reach their callbacks`() {
        show(LiveFixtures.playing)

        compose.onNodeWithText("Purobot Max Pro 2").assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.BADGE).assertIsDisplayed()
        compose.onNodeWithText("Live · 0.4 s").assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.TRANSPORT).assertDoesNotExist()
        compose.onNodeWithTag(LiveTestTags.STATUS).assertDoesNotExist()

        compose.onNodeWithContentDescription("Turn sound on").performClick()
        compose.onNodeWithContentDescription("Save a snapshot").performClick()
        compose.onNodeWithContentDescription("Full screen").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("mute", "snapshot", "fullscreen:true", "back"), calls)
    }

    @Test
    fun `the camera's recent clips are under the controls, each opening its clip, the rest in History`() {
        show(LiveFixtures.playing)

        compose.onNodeWithTag(LiveTestTags.SCREEN).performScrollToNode(hasTestTag(RecentClipsTestTags.SECTION))
        compose.onNodeWithText("Recent videos").assertIsDisplayed()
        compose.onNodeWithTag(RecentClipsTestTags.clip(12)).performClick()
        compose.onNodeWithTag(LiveTestTags.SCREEN).performScrollToNode(hasText("More videos in History"))
        compose.onNodeWithText("More videos in History").performClick()

        assertEquals(listOf("clip:Highlight", "all-clips"), calls)
    }

    @Test
    fun `when the clips cannot be read, their retry asks again`() {
        val failed = LiveFixtures.clips.copy(clips = emptyList(), refreshed = false, error = DomainError.Network("x"))
        show(LiveFixtures.playing.copy(clips = failed))

        compose.onNodeWithTag(LiveTestTags.SCREEN).performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Retry").performClick()

        assertEquals(listOf("retry-clips"), calls)
    }

    @Test
    fun `talk is shown disabled, with the reason`() {
        show(LiveFixtures.playing)

        compose.onNode(hasText("Talk") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Talking through the camera is not supported.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `on the fallback it says HLS, with sound on and the snapshot's result`() {
        show(LiveFixtures.playingHls)

        compose.onNodeWithText("HLS").assertIsDisplayed()
        compose.onNodeWithText("Live · 3.2 s").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mute").assertIsDisplayed()
        compose.onNodeWithText("Snapshot saved in your pictures.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `starting says so, with no badge`() {
        show(LiveFixtures.connecting)

        compose.onNodeWithText("Connecting…").assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.BADGE).assertDoesNotExist()
    }

    @Test
    fun `waiting to start again says why`() {
        show(LiveFixtures.waiting)

        compose.onNodeWithText("Reconnecting… The connection to the server broke.").assertIsDisplayed()
        compose.onNodeWithText("HLS").assertIsDisplayed()
    }

    @Test
    fun `stopped, it says why and the user starts it again`() {
        show(LiveFixtures.stopped)

        compose.onNodeWithText("The camera's stream ended.").assertIsDisplayed()
        compose.onNodeWithText("Watch again").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `too many viewers is said as such`() {
        show(LiveFixtures.tooManyViewers)

        compose.onNodeWithText("Too many people are watching. Try again in a moment.").assertIsDisplayed()
    }

    @Test
    fun `full screen keeps the controls over the picture and leads back out`() {
        show(LiveFixtures.fullscreen)

        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Talk").assertDoesNotExist()
        compose.onNodeWithText("Live · 0.4 s").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit full screen").performClick()
        assertEquals(listOf("fullscreen:false"), calls)
    }

    @Test
    fun `a device without a camera says so, with no controls`() {
        show(LiveFixtures.notACamera)

        compose.onNodeWithText("This device has no camera.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save a snapshot").assertDoesNotExist()
        compose.onNodeWithText("Talk").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h900dp")
    fun `in French, the delay has a decimal comma`() {
        show(LiveFixtures.playing)

        compose.onNodeWithText("Direct · 0,4 s").assertIsDisplayed()
    }
}
