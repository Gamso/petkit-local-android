package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
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
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.live.LiveTestTags

/**
 * Live video end to end, against the mock's test pattern: the live card at the top of a camera's
 * page shows the camera's latest still and nothing plays until it is tapped; then its live video
 * plays over the WebSocket; the socket closing with 1013 (the phone read too slowly)
 * moves it to HLS by itself; and leaving the screen closes what it had open, which the mock
 * counts (`GET /mock`: `live_sockets`, `hls_views`).
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
    fun theLiveCardShowsTheLatestStillAndPlaysOnlyWhenTapped() {
        signIn()
        openDevice(PUROBOT)
        // The mock's camera visits all have a snapshot: the card names when the newest was taken.
        waitFor(hasTestTag(LiveCardTestTags.CARD) and hasContentDescription("Still from", substring = true))
        check(mockCount("live_sockets") == 0) { "the page opened a live socket by itself" }

        compose.onNodeWithTag(LiveCardTestTags.CARD).performClick()
        waitFor(hasTestTag(LiveTestTags.SCREEN))
        waitFor(hasTestTag(LiveTestTags.BADGE))
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }
    }

    @Test
    fun leavingTheLiveVideoClosesItsSocket() {
        signIn()
        openLive(PUROBOT)
        waitFor(hasTestTag(LiveTestTags.BADGE))
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
        waitUntil("no live socket") { mockCount("live_sockets") == 0 }
    }

    @Test
    fun closeCode1013FallsBackToHls() {
        signIn()
        openLive(PUROBOT)
        // Playing over the WebSocket first.
        waitFor(hasTestTag(LiveTestTags.BADGE))
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }
        compose.onNodeWithTag(LiveTestTags.TRANSPORT).assertDoesNotExist()

        MockServer.control("POST", "live/close", """{"code":1013}""")

        // HLS at once, and playing again: the badge comes back once its first frame shows.
        waitFor(hasTestTag(LiveTestTags.TRANSPORT))
        waitFor(hasTestTag(LiveTestTags.BADGE))
        waitUntil("an HLS view and no socket") { mockCount("hls_views") == 1 && mockCount("live_sockets") == 0 }

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
        // An HLS view has no connection to close: it lapses once nothing asks for it (5 s).
        waitUntil("the HLS view lapsed") { mockCount("hls_views") == 0 }
        check(mockCount("live_sockets") == 0) { "a live socket is still open" }
    }

    /** From Home to [deviceId]'s page, then the live card at its top. */
    private fun openLive(deviceId: Long) {
        openDevice(deviceId)
        compose.onNodeWithTag(LiveCardTestTags.CARD).performClick()
        waitFor(hasTestTag(LiveTestTags.SCREEN))
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
        const val MS = 1000
    }
}
