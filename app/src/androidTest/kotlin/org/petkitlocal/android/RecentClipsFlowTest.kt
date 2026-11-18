package org.petkitlocal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.ui.LiveCardTestTags
import org.petkitlocal.core.ui.RecentClipsTestTags
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.litter.PageTestTags
import org.petkitlocal.feature.history.HistoryTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.live.LiveTestTags

/**
 * A camera's recent clips end to end, against the mock: its page lists the clips its history
 * holds, and one of them plays in the clip player, read through the app's authenticated client;
 * opened from under the live video, the clip stops the live stream, which comes back after it.
 */
@MockOnly("the mock's clips, with its scenario stopped through the control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class RecentClipsFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** No new visit while the test taps a clip: the row stays as it was read. */
    @Before
    fun quietMock() {
        MockServer.control("PUT", "scenario", """{"interval_sec":0}""")
    }

    @Test
    fun aRecentClipOnACameraPagePlays() {
        signIn()
        openDevice(PUROBOT)

        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(RecentClipsTestTags.SECTION))
        waitFor(aClip)
        compose.onAllNodes(aClip).onFirst().performScrollTo().performClick()

        waitFor(hasTestTag(HistoryTestTags.CLIP_PLAYER))
        // Its length is known once the player has read the file through the bearer-carrying client.
        waitFor(hasText("/ 0:02", substring = true))
        compose.onNode(hasText("This video could not be played.")).assertDoesNotExist()

        compose.onNodeWithContentDescription("Close").performClick()
        // Back on the page where it was left, scrolled down to the clips: the status line at the
        // top may be out of the list's composition by now, the day's activity standing between.
        waitFor(hasTestTag(RecentClipsTestTags.SECTION))
    }

    @Test
    fun aClipOpenedUnderTheLiveVideoStopsItUntilItIsClosed() {
        signIn()
        openDevice(PUROBOT)
        compose.onNodeWithTag(LiveCardTestTags.CARD).performClick()
        waitFor(hasTestTag(LiveTestTags.BADGE))
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }

        compose.onNodeWithTag(LiveTestTags.SCREEN).performScrollToNode(hasTestTag(RecentClipsTestTags.SECTION))
        waitFor(aClip)
        compose.onAllNodes(aClip).onFirst().performScrollTo().performClick()

        waitFor(hasTestTag(HistoryTestTags.CLIP_PLAYER))
        waitFor(hasText("/ 0:02", substring = true))
        waitUntil("no live socket while the clip plays") { mockCount("live_sockets") == 0 }

        compose.onNodeWithContentDescription("Close").performClick()
        waitFor(hasTestTag(LiveTestTags.BADGE))
        waitUntil("the live socket again") { mockCount("live_sockets") == 1 }
    }

    /** Any clip of the recent clips row. */
    private val aClip = SemanticsMatcher("is a recent clip") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.let(RecentClipsTestTags::isClip) == true
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

        /** The mock's T5, a camera litter box with a week of visits and their clips. */
        const val PUROBOT = 1003L
        const val TIMEOUT_MS = 30_000L
        const val MS = 1000
    }
}
