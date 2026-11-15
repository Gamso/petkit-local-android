package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * Home end to end, on a device, against the mock server (core:e2e): sign in through the sign-in
 * screen, and Home draws the mock's household from the cache its refresh filled. Then the mock
 * takes a device offline, a pull to refresh shows it on its card, and the card opens the device
 * screen. The test signs out at the end, so it starts and ends signed out like the others.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class HomeFlowTest {
    @get:Rule(order = 0)
    val mock = MockServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsTheMockHouseholdAndFollowsARefresh() {
        signIn()
        val list = compose.onNodeWithTag(HomeTestTags.LIST)
        val box = hasTestTag(HomeTestTags.device(PURA_MAX))
        waitFor(box)

        // Every family of the mock: litter boxes, feeders, fountains, and the pets. The Pura Air
        // has no card (its level is on the Pura Max's page); a Bluetooth fountain names its relay.
        compose.onNodeWithTag(HomeTestTags.device(PURA_AIR)).assertDoesNotExist()
        list.scrollAndFind("Fresh Element Solo")
        list.scrollAndFind("EverSweet Ultra AI")
        list.scrollAndFind("via Fresh Element Solo")
        list.scrollAndFind("Mochi")

        // The server takes the Pura Max offline; Home shows it once the user pulls to refresh,
        // never before (ADR 0002: the screen follows the cache).
        MockServer.control("POST", "devices/$PURA_MAX/offline")
        list.performScrollToIndex(0)
        list.performTouchInput { swipeDown() }
        list.performScrollToNode(box)
        waitFor(box and hasText("Offline", substring = true))

        // A card opens its device.
        compose.onNode(box).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        signOut()
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

    private fun SemanticsNodeInteraction.scrollAndFind(text: String) {
        performScrollToNode(hasText(text))
        compose.onNodeWithText(text, useUnmergedTree = true).assertExists()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val PURA_MAX = 1002L
        const val PURA_AIR = 4001L

        // Home's first refresh reads 13 device details and the day's timeline from the mock.
        const val TIMEOUT_MS = 30_000L
    }
}
