package org.petkitlocal.android

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.live.LiveTestTags

/**
 * Talking through a camera end to end, from its page's live card, against the mock's talk route: holding Talk opens the
 * socket and the phone's microphone reaches the server (an emulator's microphone records silence,
 * which is still audio: the mock counts its bytes, `GET /mock`'s `talk_bytes`), letting go closes
 * it; an error the server sends mid-talk is said in place; and a camera without the patcher says
 * what to do without opening anything.
 */
@MockOnly("the mock's talk route, its byte count and its control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class TalkFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun allowTheMicrophone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
    }

    @Test
    fun holdingTalkSendsTheMicrophoneToTheCameraAndLettingGoEndsIt() {
        signIn()
        openLive(PUROBOT)
        val talk = compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo()

        talk.performTouchInput { down(center) }
        waitFor(hasTestTag(LiveTestTags.TALK_INDICATOR))
        waitUntil("audio reaching the mock") { mockCount("talk_sockets") == 1 && mockLong("talk_bytes") > 0 }
        // The camera's own sound is off while talking.
        compose.onNodeWithContentDescription("Turn sound on").assertExists()

        talk.performTouchInput { up() }
        waitUntil("the talk closed") { mockCount("talk_sockets") == 0 }
        compose.waitUntilDoesNotExist(hasTestTag(LiveTestTags.TALK_INDICATOR), TIMEOUT_MS)
        waitFor(hasTestTag(LiveTestTags.TALK) and isEnabled())
    }

    @Test
    fun anErrorFromTheServerMidTalkIsSaidInPlace() {
        signIn()
        openLive(PUROBOT)
        val talk = compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo()
        talk.performTouchInput { down(center) }
        waitFor(hasTestTag(LiveTestTags.TALK_INDICATOR))

        MockServer.control("POST", "talk/error", """{"code":"busy"}""")

        waitFor(hasText("Someone else is talking through this camera. Try again when they stop."))
        talk.performTouchInput { up() }
        waitUntil("the talk closed") { mockCount("talk_sockets") == 0 }
    }

    @Test
    fun aCameraWithoutThePatcherSaysWhatToDoAndOpensNothing() {
        signIn()
        openLive(PUROBOT_ULTRA)
        waitFor(hasText("Needs the Two-Way Talk patcher."))

        compose.onNodeWithTag(LiveTestTags.TALK).performScrollTo().performClick()

        waitFor(
            hasText(
                "Apply the Two-Way Talk patcher to this camera in the petkit-local panel (Patchers tab), then try again.",
            ),
        )
        check(mockCount("talk_sockets") == 0) { "a talk was opened" }
        check(mockLong("talk_bytes") == 0L) { "audio was sent" }
    }

    /** From Home to [deviceId]'s page, whose live card has Talk under it. */
    private fun openLive(deviceId: Long) {
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(deviceId))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
        waitFor(hasTestTag(LiveCardTestTags.CARD))
        waitFor(hasTestTag(LiveTestTags.TALK))
    }

    private fun mockState() = JSONObject(MockServer.control("GET", ""))

    /** One of the counts `GET /mock` reports. */
    private fun mockCount(name: String): Int = mockState().getInt(name)

    private fun mockLong(name: String): Long = mockState().getLong(name)

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

        /** The mock's T5, a camera that can be talked through. */
        const val PUROBOT = 1003L

        /** The mock's T6, a camera without the Two-Way Talk patcher. */
        const val PUROBOT_ULTRA = 1004L
        const val TIMEOUT_MS = 30_000L
        const val MS = 1000
    }
}
