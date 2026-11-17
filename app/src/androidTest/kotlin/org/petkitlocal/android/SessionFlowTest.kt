package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * Sign-in and the end of a session, on a device, against the mock server (mock-server/) or the
 * real one, with the app's real data layer: DataStore, the Keystore, OkHttp. The server's address
 * and password, and what its panel does to sessions, come from core:e2e (`TestServer`), and
 * `TestServerRule` grants the local network permission and resets the server before each test.
 *
 * Each test starts and ends signed out, so they run in any order on a fresh install.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class SessionFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun startOnSignIn() {
        compose.waitUntilExactlyOneExists(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
    }

    @Test
    fun aWrongPasswordIsSaidInPlaceThenSigningInAndOutWorks() {
        enterAddress()
        // The button is never greyed out for a missing field: pressing it says what is missing.
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Enter the password."), TIMEOUT_MS)

        signInWith("not-the-password")
        compose.waitUntilAtLeastOneExists(hasText("Wrong password."), TIMEOUT_MS)

        signInWith(TestServer.password)
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
        assertEquals(1, TestServer.sessions().size)

        openSettings()
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onLast().performClick()

        compose.waitUntilExactlyOneExists(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
        assertEquals(0, TestServer.sessions().size)
        compose.onNodeWithTag(SignInTestTags.SESSION_ENDED).assertDoesNotExist() // signed out by hand
    }

    @Test
    fun aSessionRevokedInThePanelSendsTheAppBackToSignInSayingWhy() {
        enterAddress()
        signInWith(TestServer.password)
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)

        TestServer.revokeSessions() // the panel's "revoke"
        // The app checks its session when it comes back to the foreground.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.waitUntilExactlyOneExists(hasTestTag(SignInTestTags.SESSION_ENDED), TIMEOUT_MS)
        compose.onAllNodesWithText("revoked in the panel", substring = true).onLast().assertExists()
    }

    private fun enterAddress() {
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(TestServer.address)
    }

    private fun signInWith(password: String) {
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(password)
        // Only the server's lockout disables the button: a wrong password locks it for the
        // server's Retry-After (1 s at first), with the countdown on it.
        compose.waitUntilExactlyOneExists(hasTestTag(SignInTestTags.SUBMIT) and isEnabled(), TIMEOUT_MS)
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
    }

    /** Home's way to Settings: a "Settings" button or an icon labelled so, whichever Home has. */
    private fun openSettings() {
        val settings = (hasText("Settings") or hasContentDescription("Settings")) and hasClickAction()
        compose.waitUntilAtLeastOneExists(settings, TIMEOUT_MS)
        compose.onAllNodes(settings).onLast().performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(SettingsTestTags.SCREEN), TIMEOUT_MS)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
