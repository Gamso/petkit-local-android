package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.QuickActionsTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * The quick actions end to end (issue #40), on a device, against the mock server (core:e2e):
 * the EverSweet Ultra AI's Drain is pinned from the expanded sheet, shows in the collapsed row
 * and in the device's settings, still asks before draining, and is still there after signing
 * out and back in to the same server: the phone keeps its choices across sessions on one server.
 * The test resets the row at the end, so the next test finds the app's own.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class QuickActionsFlowTest {
    @get:Rule(order = 0)
    val mock = MockServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val drainInSheet = hasText("Drain") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET))

    @Test
    fun aPinnedActionShowsInTheCollapsedRowAndOutlivesTheSession() {
        signIn()
        openFountain()
        // The app's row: Refill and Drain & Flush; Drain cannot be undone, so it is under More.
        compose.onNode(drainInSheet).assertDoesNotExist()

        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).performClick()
        waitFor(hasTestTag(QuickActionsTestTags.EDITOR))
        compose.onNodeWithTag(QuickActionsTestTags.other(DRAIN)).performScrollTo().performClick()
        waitFor(hasTestTag(QuickActionsTestTags.inView(DRAIN)))
        compose.onNodeWithTag(QuickActionsTestTags.DONE).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(QuickActionsTestTags.EDITOR), TIMEOUT_MS)

        // In the collapsed row now (the sheet folded itself away for the editor).
        waitFor(drainInSheet)
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).assertDoesNotExist()
        // Pinned, it still asks first.
        compose.onNode(drainInSheet).performClick()
        waitFor(hasText("Drain?"))
        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        compose.waitUntilDoesNotExist(hasText("Drain?"), TIMEOUT_MS)

        // The device's settings say so too.
        compose.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        waitFor(hasTestTag(DeviceTestTags.SETTINGS_SCREEN))
        val row = hasTestTag(QuickActionsTestTags.SETTINGS_ROW)
        compose.onNodeWithTag(DeviceTestTags.SETTINGS_LIST).performScrollToNode(row)
        // The row's summary names the actions in view, in order: Drain was added last.
        compose.onNode(row and hasText("Refill, Drain & Flush, Drain", substring = true)).assertExists()
        back()
        back()

        // A new session on the same server keeps the phone's choice.
        waitFor(hasTestTag(HomeTestTags.LIST))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        signOut()
        signIn()
        openFountain()
        waitFor(drainInSheet)

        // Reset to default gives the app's row back, Drain under More again.
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).performClick()
        waitFor(hasTestTag(QuickActionsTestTags.EDITOR))
        compose.onNodeWithTag(QuickActionsTestTags.RESET).performScrollTo().performClick()
        waitFor(hasTestTag(QuickActionsTestTags.other(DRAIN)))
        compose.onNodeWithTag(QuickActionsTestTags.DONE).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(QuickActionsTestTags.EDITOR), TIMEOUT_MS)
        compose.onNode(drainInSheet).assertDoesNotExist()

        back()
        waitFor(hasTestTag(HomeTestTags.LIST))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        signOut()
    }

    private fun openFountain() {
        // The grid is lazy: the first card shows the cache is read, then the fountain is scrolled to.
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(FOUNTAIN))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasText("Idle") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))
        compose.onNodeWithText("EverSweet Ultra AI").assertExists()
        waitFor(hasTestTag(DeviceTestTags.SHEET_MORE))
    }

    private fun signIn() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(MockServer.baseUrl.toString().removeSuffix("/"))
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(MockServer.PASSWORD)
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
        const val FIRST_CARD = 1001L
        const val DRAIN = "fountain_drain"
        const val TIMEOUT_MS = 30_000L
    }
}
