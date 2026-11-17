package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.network.dto.DeviceKind
import org.petkitlocal.core.network.dto.DeviceSummaryDto
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.history.HistoryTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * Whatever household the server has, on a device: Home draws a card for each device the server
 * lists, each card opens its device's page and that page its settings, History opens on every
 * device, and a session revoked in the panel while a page holds the live socket sends the app
 * back to sign-in. It reads the household from the server rather than knowing it, so it runs
 * against the real server (seeded by its own smoke test) as well as the mock.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class ServerHouseholdFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** The devices Home has a card for, read from the server by a session closed at once. */
    private lateinit var cards: List<DeviceSummaryDto>

    @Before
    fun readTheHousehold() {
        cards = runBlocking {
            val api = TestServer.signedInApi()
            val devices = api.listDevices().devices
            api.logout()
            val ids = devices.map { it.id }.toSet()
            // A Pura Air whose litter box is listed has no card: the box's page shows it.
            devices.filterNot { it.kind == DeviceKind.PURIFIER && it.parentId in ids }
        }
        check(cards.isNotEmpty()) { "${TestServer.baseUrl} lists no device with a card of its own" }
    }

    @Test
    fun homeShowsEveryDeviceAndEachOpensItsPageAndSettings() {
        signIn()
        for (device in cards) {
            val card = hasTestTag(HomeTestTags.device(device.id))
            scrollHomeTo(card)
            compose.onNode(card).performClick()

            // Its page, under its model's name, and its settings behind the header's gear.
            waitFor(hasTestTag(DeviceTestTags.STATUS))
            waitFor(hasText(device.model))
            compose.onNode(hasContentDescription("Settings") and hasClickAction()).performClick()
            waitFor(hasTestTag(DeviceTestTags.SETTINGS_SCREEN))
            back()
            waitFor(hasTestTag(DeviceTestTags.STATUS))
            back()
            waitFor(hasTestTag(HomeTestTags.LIST))
        }

        // History, from Home, for every device: the device picker says so when there is a choice.
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToIndex(0)
        compose.onNodeWithContentDescription("History").performClick()
        waitFor(hasTestTag(HistoryTestTags.SCREEN))
        if (cards.size > 1) waitFor(hasContentDescription("Show the history of: All devices"))
        back()

        signOut()
        assertEquals(0, TestServer.sessions().size)
    }

    @Test
    fun aSessionRevokedInThePanelWhileADevicePageIsOpenReturnsToSignIn() {
        signIn()
        val card = hasTestTag(HomeTestTags.device(cards.first().id))
        scrollHomeTo(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))

        // Only the live socket can tell, with close code 4401: nothing else is asked meanwhile.
        TestServer.revokeSessions()

        waitFor(hasTestTag(SignInTestTags.SCREEN))
    }

    /** Home's list is lazy and filled from the cache its refresh writes: scroll once it has the card. */
    private fun scrollHomeTo(card: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card) }.isSuccess
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
        const val TIMEOUT_MS = 30_000L
    }
}
