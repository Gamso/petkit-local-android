package org.petkitlocal.android

import android.content.ClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.component.PkConfirmTestTags
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * A device end to end, on a device, against the mock server (core:e2e): the page of the
 * EverSweet Ultra AI follows the mock over `/ws`, its settings open on its identity card, whose
 * Copy button puts its serial number on the clipboard, its settings are written through
 * `PUT /entities/{key}` and come back from the server's answer, its actions report how they
 * were delivered, and a session ended from the server while the socket is open (close code
 * 4401) sends the app back to sign-in.
 */
@MockOnly("the mock's EverSweet Ultra AI, taken offline and back through the control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class DeviceFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aDeviceFollowsTheServerLiveAndTakesSettingsAndActions() {
        signIn()
        openFountain()

        // The mock takes the fountain offline: the open screen shows it over /ws, no refresh.
        MockServer.control("POST", "devices/$FOUNTAIN/offline")
        waitFor(hasText("Offline") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))
        MockServer.control("POST", "devices/$FOUNTAIN/online")
        waitFor(hasText("Idle") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))

        // A button that can be undone goes at once and says how it was delivered.
        compose.onNode(hasText("Refill") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET))).performClick()
        waitFor(hasText("Sent to the device."))
        // A destructive one is behind More, and asks first.
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNode(hasText("Drain") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET))).performClick()
        waitFor(hasText("Drain?"))
        // The confirm button takes taps once the dialog has been open a moment.
        val confirm = hasTestTag(PkConfirmTestTags.CONFIRM) and hasText("Drain")
        waitFor(confirm)
        compose.onNode(confirm).performClick()
        waitFor(hasTestTag(DeviceTestTags.FEEDBACK) and hasText("Drain") and hasText("Sent to the device."))

        // Settings open on the identity card: Copy puts the serial number on the clipboard.
        compose.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        waitFor(hasTestTag(DeviceTestTags.SETTINGS_SCREEN))
        waitFor(hasText(FOUNTAIN_SERIAL) and hasAnyAncestor(hasTestTag(DeviceTestTags.IDENTITY)))
        compose.onNodeWithTag(DeviceTestTags.COPY_SERIAL).performClick()
        waitFor(hasTestTag(DeviceTestTags.COPY_SERIAL) and hasText("Copied"))
        compose.activityRule.scenario.onActivity { activity ->
            val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip
            assertEquals(FOUNTAIN_SERIAL, clip?.getItemAt(0)?.text?.toString())
        }

        // Then the settings: a switch, then a number through its editor.
        compose.onNodeWithTag(DeviceTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(DeviceTestTags.entity("child_lock")))
        val lock = compose.onNodeWithTag(DeviceTestTags.entity("child_lock"))
        lock.assertIsOff()
        lock.performClick()
        // It moves once the server answered (no optimistic UI), and stays: the cache holds it.
        waitFor(hasTestTag(DeviceTestTags.entity("child_lock")) and isOn())
        lock.assertIsOn()

        compose.onNodeWithTag(DeviceTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(DeviceTestTags.entity("fountain_time")))
        compose.onNodeWithTag(DeviceTestTags.entity("fountain_time")).performClick()
        waitFor(hasTestTag(DeviceTestTags.EDITOR))
        compose.onNode(hasContentDescription("Increase")).performClick()
        compose.onNodeWithTag(DeviceTestTags.EDITOR_SAVE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(DeviceTestTags.EDITOR), TIMEOUT_MS)
        waitFor(hasText("4 min"))

        back()
        back()
        waitFor(hasTestTag(HomeTestTags.LIST))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        signOut()
    }

    @Test
    fun aSessionEndedWhileTheSocketIsOpenReturnsToSignIn() {
        signIn()
        openFountain()

        // Revoked from the panel: only the socket can tell, with close code 4401.
        TestServer.revokeSessions()

        waitFor(hasTestTag(SignInTestTags.SCREEN))
    }

    private fun openFountain() {
        // The grid is lazy: the first card shows the cache is read, then the fountain is scrolled to.
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(FOUNTAIN))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasText("Idle") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))
        compose.onNodeWithText("EverSweet Ultra AI").assertExists()
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

    private fun signOut() {
        val settings = hasContentDescription("Settings") and hasClickAction()
        waitFor(settings)
        compose.onNode(settings).performClick()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onLast().performClick()
        waitFor(hasTestTag(SignInTestTags.SCREEN))
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FOUNTAIN = 3001L

        /** The mock's serial number for its EverSweet Ultra AI (mock-server Seed). */
        const val FOUNTAIN_SERIAL = "20250610W7H00077"
        const val FIRST_CARD = 1001L
        const val TIMEOUT_MS = 30_000L
    }
}
