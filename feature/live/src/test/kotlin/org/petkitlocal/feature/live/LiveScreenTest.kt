package org.petkitlocal.feature.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
        onTalkPressed = { calls += "talk-press" },
        onTalkReleased = { calls += "talk-release" },
        onTalkToggled = { calls += "talk-toggle" },
        onOpenAppSettings = { calls += "app-settings" },
    )

    private fun show(state: LiveUiState) {
        compose.setContent { LiveScreen(state, callbacks) }
    }

    @Test
    fun `playing, the badge says live, with no delay, and the controls reach their callbacks`() {
        show(LiveFixtures.playing)

        compose.onNodeWithText("Purobot Max Pro 2").assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.BADGE).assertIsDisplayed()
        compose.onNodeWithText("Live").assertIsDisplayed()
        compose.onNodeWithText("Live · ", substring = true).assertDoesNotExist()
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
    fun `talk is held to speak, a finger down pressing it and up letting it go`() {
        show(LiveFixtures.playing)

        compose.onNodeWithText("Hold to talk", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.TALK).assertIsEnabled().performTouchInput { down(center) }
        assertEquals(listOf("talk-press"), calls)
        compose.onNodeWithTag(LiveTestTags.TALK).performTouchInput { up() }
        assertEquals(listOf("talk-press", "talk-release"), calls)
        compose.onNodeWithTag(LiveTestTags.TALK_REASON).assertDoesNotExist()
    }

    @Test
    fun `sliding off Talk lets it go too`() {
        show(LiveFixtures.playing)

        compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo().performTouchInput {
            down(center)
            moveTo(center + Offset(0f, height * 3f))
        }
        assertEquals(listOf("talk-press", "talk-release"), calls)
    }

    @Test
    fun `TalkBack reads Talk with what a double tap does, and its double tap toggles`() {
        show(LiveFixtures.playing)

        val talk = compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo()
        talk.assert(hasContentDescription("Talk") and hasClickAction())
        talk.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("start talking", talk.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        talk.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("talk-toggle"), calls)
    }

    @Test
    fun `talking is plain to see and to hear, and the camera's sound shows as off`() {
        show(LiveFixtures.talking)

        val talk = compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo()
        compose.onNodeWithText("Talking · release to stop", useUnmergedTree = true).assertIsDisplayed()
        talk.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Talking"))
        assertEquals("stop talking", talk.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        compose.onNodeWithTag(LiveTestTags.TALK_INDICATOR)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("The camera plays what you say. Its own sound is off meanwhile.").assertExists()
        compose.onNodeWithContentDescription("Turn sound on").assertIsDisplayed()
    }

    @Test
    fun `connecting says so while held, and a double tap cancels`() {
        show(LiveFixtures.talkConnecting)

        val talk = compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Connecting… keep holding", useUnmergedTree = true).assertExists()
        talk.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Connecting"))
        assertEquals("stop connecting", talk.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }

    @Test
    fun `while the camera finishes, Talk is disabled`() {
        show(LiveFixtures.talkStopping)

        compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(LiveTestTags.TALK).performTouchInput { click() }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a camera that cannot talk keeps Talk pressable, with the reason under it`() {
        show(LiveFixtures.talkNeedsPatcher)

        compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Talk", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(LiveTestTags.TALK_REASON).assertIsDisplayed()
        compose.onNodeWithText("Needs the Two-Way Talk patcher.").assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.TALK).performTouchInput { click() }
        assertEquals(listOf("talk-press", "talk-release"), calls)
    }

    @Test
    fun `pressed anyway, it says what to do, and TalkBack hears it`() {
        show(LiveFixtures.talkNeedsPatcherPressed)

        val fix = "Apply the Two-Way Talk patcher to this camera in the petkit-local panel (Patchers tab), " +
            "then try again."
        compose.onNodeWithText(fix).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag(LiveTestTags.TALK_REASON).assertDoesNotExist()
    }

    @Test
    fun `a refused microphone says how to allow it, with the way to Android's settings`() {
        show(LiveFixtures.microphoneDenied)

        compose.onNodeWithText(
            "The microphone is not allowed. Allow it for this app in Android's settings to talk through the camera.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open settings").performScrollTo().performClick()
        assertEquals(listOf("app-settings"), calls)
    }

    @Test
    fun `an error from the server is said in place`() {
        show(LiveFixtures.talkBusy)

        compose.onNodeWithText("Someone else is talking through this camera. Try again when they stop.")
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h900dp")
    fun `in French, Talk says to hold it`() {
        show(LiveFixtures.playing)

        compose.onNodeWithText("Maintenir pour parler", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LiveTestTags.TALK).assert(hasContentDescription("Parler"))
    }

    @Test
    fun `on the fallback it says HLS, with sound on and the snapshot's result`() {
        show(LiveFixtures.playingHls)

        compose.onNodeWithText("HLS").assertIsDisplayed()
        compose.onNodeWithText("Live").assertIsDisplayed()
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
        compose.onNodeWithText("Live").assertIsDisplayed()
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
    fun `in French, the badge says Direct, with no delay`() {
        show(LiveFixtures.playing)

        compose.onNodeWithTag(LiveTestTags.BADGE).assertIsDisplayed()
        compose.onNodeWithText("Direct").assertIsDisplayed()
    }
}
