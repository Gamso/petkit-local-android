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
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.QuickActionsTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * The quick actions end to end (issue #40), on a device, against the mock server (core:e2e):
 * on the Pura Max, Level Litter is taken out of the row and Dump Litter pinned in its place from
 * the expanded sheet; Dump Litter shows in the collapsed row and in the device's settings, still
 * asks before dumping, and is still there after signing out and back in to the same server: the
 * phone keeps its choices across sessions on one server. The test resets the row at the end, so
 * the next test finds the app's own. (A fountain whose mode can be set shows its mode there
 * instead, FountainFlowTest.)
 */
@MockOnly("the mock's Pura Max and its actions")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class QuickActionsFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val dumpInSheet = hasText("Dump Litter") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET))

    @Test
    fun aPinnedActionShowsInTheCollapsedRowAndOutlivesTheSession() {
        signIn()
        openLitterBox()
        // The app's row: Scoop, Deodorize and Level Litter; Dump Litter cannot be undone, so it
        // is under More.
        compose.onNode(dumpInSheet).assertDoesNotExist()

        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).performClick()
        waitFor(hasTestTag(QuickActionsTestTags.EDITOR))
        // The row is full: one out first, then Dump Litter in.
        compose.onNode(hasContentDescription("Remove Level Litter from quick actions")).performScrollTo().performClick()
        waitFor(hasTestTag(QuickActionsTestTags.other(LEVEL)))
        compose.onNodeWithTag(QuickActionsTestTags.other(DUMP)).performScrollTo().performClick()
        waitFor(hasTestTag(QuickActionsTestTags.inView(DUMP)))
        compose.onNodeWithTag(QuickActionsTestTags.DONE).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(QuickActionsTestTags.EDITOR), TIMEOUT_MS)

        // In the collapsed row now (the sheet folded itself away for the editor).
        waitFor(dumpInSheet)
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).assertDoesNotExist()
        // Pinned, it still asks first.
        compose.onNode(dumpInSheet).performClick()
        waitFor(hasText("Dump Litter?"))
        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        compose.waitUntilDoesNotExist(hasText("Dump Litter?"), TIMEOUT_MS)

        // The device's settings say so too.
        compose.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
        waitFor(hasTestTag(DeviceTestTags.SETTINGS_SCREEN))
        val row = hasTestTag(QuickActionsTestTags.SETTINGS_ROW)
        compose.onNodeWithTag(DeviceTestTags.SETTINGS_LIST).performScrollToNode(row)
        // The row's summary names the actions in view, in order: Dump Litter was added last.
        compose.onNode(row and hasText("Scoop, Deodorize, Dump Litter", substring = true)).assertExists()
        back()
        back()

        // A new session on the same server keeps the phone's choice.
        waitFor(hasTestTag(HomeTestTags.LIST))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        signOut()
        signIn()
        openLitterBox()
        waitFor(dumpInSheet)

        // Reset to default gives the app's row back, Dump Litter under More again.
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).performClick()
        waitFor(hasTestTag(QuickActionsTestTags.EDITOR))
        compose.onNodeWithTag(QuickActionsTestTags.RESET).performScrollTo().performClick()
        waitFor(hasTestTag(QuickActionsTestTags.other(DUMP)))
        compose.onNodeWithTag(QuickActionsTestTags.DONE).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(QuickActionsTestTags.EDITOR), TIMEOUT_MS)
        compose.onNode(dumpInSheet).assertDoesNotExist()

        back()
        waitFor(hasTestTag(HomeTestTags.LIST))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        signOut()
    }

    private fun openLitterBox() {
        // The grid is lazy: the first card shows the cache is read, then the Pura Max is scrolled to.
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(PURA_MAX))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
        compose.onNodeWithText("Pura Max").assertExists()
        waitFor(hasTestTag(DeviceTestTags.SHEET_MORE))
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
        const val PURA_MAX = 1002L
        const val FIRST_CARD = 1001L
        const val DUMP = "dump_litter"
        const val LEVEL = "level_litter"
        const val TIMEOUT_MS = 30_000L
    }
}
