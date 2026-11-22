package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.ui.LiveCardTestTags
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.live.LiveTestTags

/**
 * Live video end to end, against the mock's test pattern: the live card at the top of a camera's
 * page opens nothing until play (issue #109), then plays its live video in place, and stops when
 * the page is left or the app goes to the background, coming back to the still and play; its full-screen button puts the same player in the whole window, on the same
 * socket; the socket closing with 1013 (the phone read too slowly) moves it to HLS by itself; and
 * every test leaves no socket open, which the mock counts (`GET /mock`: `live_sockets`,
 * `hls_views`).
 */
@MockOnly("the mock's live stream, closed through the control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class LiveFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theCameraPageOpensNothingUntilPlayThenPlaysInPlaceUntilLeft() {
        signIn()
        openDevice(PUROBOT)
        // The page shows the still and play (issue #109): no socket, no viewer slot.
        waitFor(hasTestTag(LiveCardTestTags.PLAY))
        assertNoSocketFor("the page opened")

        play()
        waitFor(cardBadge)
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }
        check(mockCount("live_sockets") == 1) { "play opened more than one viewer" }

        // Tapping the video stays on the page.
        compose.onNodeWithTag(LiveCardTestTags.CARD).performClick()
        compose.onNodeWithTag(LiveTestTags.SCREEN).assertDoesNotExist()

        leaveToHome()
    }

    @Test
    fun backAndReturnShowTheStillAndPlayAgain() {
        signIn()
        openDevice(PUROBOT)
        play()
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }
        leaveToHome()

        openDevice(PUROBOT)
        waitFor(hasTestTag(LiveCardTestTags.PLAY))
        assertNoSocketFor("the page came back")
    }

    @Test
    fun theCameraPageStopsItsLiveVideoInTheBackgroundAndWaitsForPlay() {
        signIn()
        openDevice(PUROBOT)
        play()
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitUntil("no live socket in the background") { mockCount("live_sockets") == 0 }

        // Back from the background: the still and play, no restart by itself.
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor(hasTestTag(LiveCardTestTags.PLAY))
        assertNoSocketFor("the app came back")

        play()
        waitFor(cardBadge)
        waitUntil("one live socket again") { mockCount("live_sockets") == 1 }

        leaveToHome()
    }

    @Test
    fun fullScreenOnAnIdleCardPlaysInPlaceWithOneViewer() {
        signIn()
        openDevice(PUROBOT)
        waitFor(hasTestTag(LiveCardTestTags.PLAY))
        assertNoSocketFor("the page opened")

        // One tap: full screen starts the video too, on one socket, on the same page.
        compose.onNodeWithTag(LiveCardTestTags.FULLSCREEN).performClick()
        waitFor(hasTestTag(LiveCardTestTags.FULLSCREEN_VIEW))
        waitFor(hasTestTag(LiveVideoTestTags.BADGE) and hasAnyAncestor(hasTestTag(LiveCardTestTags.FULLSCREEN_VIEW)))
        compose.onNodeWithTag(LiveTestTags.SCREEN).assertDoesNotExist()
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }
        check(mockCount("live_sockets") == 1) { "full screen opened another viewer" }
        compose.onNodeWithContentDescription("Save a snapshot").assertExists()

        // Back leaves full screen, not the page, and the card plays on.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntilDoesNotExist(hasTestTag(LiveCardTestTags.FULLSCREEN_VIEW), TIMEOUT_MS)
        waitFor(cardBadge)
        check(mockCount("live_sockets") == 1) { "leaving full screen opened another viewer" }

        leaveToHome()
    }

    @Test
    fun closeCode1013FallsBackToHls() {
        signIn()
        openDevice(PUROBOT)
        play()
        // Playing over the WebSocket first.
        waitFor(cardBadge)
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }
        compose.onNodeWithTag(LiveVideoTestTags.TRANSPORT).assertDoesNotExist()

        MockServer.control("POST", "live/close", """{"code":1013}""")

        // HLS at once, and playing again: the badge comes back once its first frame shows.
        waitFor(hasTestTag(LiveVideoTestTags.TRANSPORT))
        waitFor(cardBadge)
        waitUntil("an HLS view and no socket") { mockCount("hls_views") == 1 && mockCount("live_sockets") == 0 }

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag(HomeTestTags.LIST))
        // An HLS view has no connection to close: it lapses once nothing asks for it (5 s).
        waitUntil("the HLS view lapsed") { mockCount("hls_views") == 0 }
        waitUntil("no live socket") { mockCount("live_sockets") == 0 }
    }

    /** Play, in the middle of the card. */
    private fun play() {
        waitFor(hasTestTag(LiveCardTestTags.PLAY))
        compose.onNodeWithTag(LiveCardTestTags.PLAY).performClick()
    }

    /** No socket opens by itself: still none a few seconds after [what]. */
    private fun assertNoSocketFor(what: String) {
        repeat(NO_SOCKET_CHECKS) {
            check(mockCount("live_sockets") == 0 && mockCount("hls_views") == 0) { "a live view opened when $what" }
            Thread.sleep(NO_SOCKET_INTERVAL_MS)
        }
    }

    /** The live badge on the page's card: it is playing. */
    private val cardBadge = hasTestTag(LiveVideoTestTags.BADGE) and hasAnyAncestor(hasTestTag(LiveCardTestTags.CARD))

    /** Back to Home from a camera's page, which closes its live video: no socket is left open. */
    private fun leaveToHome() {
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag(HomeTestTags.LIST))
        waitUntil("no live socket once the page is left") { mockCount("live_sockets") == 0 }
    }

    /** From Home to [deviceId]'s page. */
    private fun openDevice(deviceId: Long) {
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(deviceId))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
    }

    /** One of the counts `GET /mock` reports. */
    private fun mockCount(name: String): Int = JSONObject(MockServer.control("GET", "")).getInt(name)

    private fun waitUntil(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(TIMEOUT_MS) { condition() }
        } catch (e: AssertionError) {
            throw AssertionError("Waited ${TIMEOUT_MS / MS} s for $what", e)
        }
    }

    private fun signIn() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(TestServer.address)
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(TestServer.password)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FIRST_CARD = 1001L

        /** The mock's T5, a camera. */
        const val PUROBOT = 1003L
        const val TIMEOUT_MS = 30_000L
        const val NO_SOCKET_CHECKS = 6
        const val NO_SOCKET_INTERVAL_MS = 500L
        const val MS = 1000
    }
}
