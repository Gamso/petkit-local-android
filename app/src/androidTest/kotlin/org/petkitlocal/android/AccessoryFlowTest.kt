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
import org.petkitlocal.core.designsystem.component.PkConfirmTestTags
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * Bluetooth accessories end to end, against the mock server: the Pura Max's page shows the N60
 * level of the Pura Air in its K3 slot and follows it over `/ws`, its pill leads to the Pura
 * Air's own screen, which leads back to the box relaying it; and a fountain whose relay polls
 * HTTP refuses a button with the parent's name (409 `accessory_unreachable`). Each test signs
 * out at the end, so it starts and ends signed out like the others.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class AccessoryFlowTest {
    @get:Rule(order = 0)
    val mock = MockServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun thePuraMaxShowsItsPuraAirsLevelAndLeadsToIt() {
        signIn()
        open(PURA_MAX)
        waitFor(hasText("Pura Max"))

        // The Pura Air's N60, on the box that has none of its own; a new level follows over /ws.
        waitFor(hasContentDescription("Pura Air · N60, 63 %"))
        MockServer.control("PUT", "devices/$PURA_AIR/state", """{"consumables.liquid": 12}""")
        val low = hasContentDescription("Pura Air · N60, 12 %, Needs attention")
        waitFor(low)

        // Its pill explains it and leads to the Pura Air, whose screen names the box relaying it.
        compose.onNode(low).performClick()
        waitFor(hasTestTag(DeviceTestTags.OPEN_ACCESSORY))
        compose.onNodeWithTag(DeviceTestTags.OPEN_ACCESSORY).performClick()
        waitFor(hasText("Pura Air K3"))
        waitFor(hasText("Reports through Pura Max"))
        compose.onNodeWithTag(DeviceTestTags.RELAY).performClick()
        waitFor(hasContentDescription("Pura Air · N60, 12 %, Needs attention"))

        signOut()
    }

    @Test
    fun aFountainRelayedOverHttpCannotBeReachedAndSaysWhy() {
        signIn()
        open(EVERSWEET_MAX)
        waitFor(hasText("Reports through Fresh Element Solo"))

        // Its filter reset is destructive: behind More, confirmed, then refused by the server.
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNode(
            hasText("Reset Filter") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET)),
        ).performClick()
        waitFor(hasText("Reset Filter?"))
        val confirm = hasTestTag(PkConfirmTestTags.CONFIRM) and hasText("Reset Filter")
        waitFor(confirm)
        compose.onNode(confirm).performClick()
        waitFor(
            hasTestTag(DeviceTestTags.FEEDBACK) and
                hasText("The Fresh Element Solo must be online (MQTT) to reach this accessory."),
        )

        signOut()
    }

    /** From Home: the grid is lazy, so the first card says the cache is read, then [deviceId]'s is scrolled to. */
    private fun open(deviceId: Long) {
        waitFor(hasTestTag(HomeTestTags.device(PURA_MAX)))
        val card = hasTestTag(HomeTestTags.device(deviceId))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
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

    /** Back to Home, then out through Settings. */
    private fun signOut() {
        while (compose.onAllNodes(hasTestTag(HomeTestTags.LIST)).fetchSemanticsNodes().isEmpty()) {
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        // The header's gear, in the lazy list's first item.
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        val settings = hasContentDescription("Settings") and hasClickAction()
        waitFor(settings)
        compose.onNode(settings).performClick()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onLast().performClick()
        waitFor(hasTestTag(SignInTestTags.SCREEN))
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val PURA_MAX = 1002L
        const val PURA_AIR = 4001L
        const val EVERSWEET_MAX = 3003L
        const val TIMEOUT_MS = 30_000L
    }
}
