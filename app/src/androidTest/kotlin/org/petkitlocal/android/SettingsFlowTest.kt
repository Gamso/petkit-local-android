package org.petkitlocal.android

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.auth.password.ChangePasswordTestTags
import org.petkitlocal.feature.settings.SettingsTestTags
import org.petkitlocal.feature.settings.licences.LicencesTestTags

/**
 * Settings end to end (issue #24), on a device, against the mock server (core:e2e): the server's
 * version read from it, a notification kind turned off and held by the server for this session,
 * the password changed after a wrong current one, the app's licence and the open-source licences
 * read from the APK, then a confirmed sign-out back to sign-in. The mock is reset before each
 * test, which also puts the password back.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @get:Rule(order = 0)
    val mock = MockServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsReadTheServerChangeItsChoicesAndSignOut() {
        signIn()
        openSettings()

        // The server's version from GET /info. Each row is one merged node.
        waitFor(hasTestTag(SettingsTestTags.SERVER_VERSION) and hasText("mock-0.1.0"))

        // A kind turned off moves once the server holds it, and the server holds it for this phone.
        val offline = hasTestTag(SettingsTestTags.kind(OFFLINE))
        waitFor(offline and isOn())
        compose.onNode(offline).performScrollTo().performClick()
        waitFor(offline and isOff() and isEnabled())
        val sessions = MockServer.control("GET", "sessions")
        assertTrue(sessions, sessions.contains("\"kinds\":[\"toilet\""))
        assertFalse(sessions, sessions.contains("\"offline\""))

        changePassword()
        readLicences()

        // Signing out is confirmed, and lands on sign-in.
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onLast().performClick()
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        assertEquals("""{"ok":true,"sessions":[]}""", MockServer.control("GET", "sessions"))
    }

    /** A wrong current password is said in place; the right one changes it and this phone stays in. */
    private fun changePassword() {
        compose.onNodeWithTag(SettingsTestTags.CHANGE_PASSWORD).performScrollTo().performClick()
        waitFor(hasTestTag(ChangePasswordTestTags.SCREEN))
        type(ChangePasswordTestTags.CURRENT, "not-the-password")
        type(ChangePasswordTestTags.NEW, NEW_PASSWORD)
        type(ChangePasswordTestTags.REPEAT, NEW_PASSWORD)
        submitAndConfirm()
        waitFor(hasText("Wrong password."))

        type(ChangePasswordTestTags.CURRENT, MockServer.PASSWORD)
        // The server's lockout after a wrong password (1 s at first) holds the button meanwhile.
        submitAndConfirm()
        waitFor(hasTestTag(ChangePasswordTestTags.DONE_CARD))
        compose.onNodeWithTag(ChangePasswordTestTags.DONE).performScrollTo().performClick()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
        // Still signed in: the new password is the server's now.
        assertTrue(MockServer.control("GET", "sessions").contains("\"id\""))
    }

    /**
     * About: the app's own MIT licence, then the open-source list generated from the direct release
     * dependencies, by project: AndroidX unfolds to its components, okhttp opens its licence.
     */
    private fun readLicences() {
        compose.onNodeWithTag(SettingsTestTags.APP_LICENCE).performScrollTo().performClick()
        waitFor(inDialog(hasText("MIT License", substring = true)))
        compose.onNodeWithTag(LicencesTestTags.CLOSE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LicencesTestTags.DIALOG), TIMEOUT_MS)

        compose.onNodeWithTag(SettingsTestTags.LICENCES).performScrollTo().performClick()
        waitFor(hasTestTag(LicencesTestTags.LIST))
        // The list is lazy and read from the APK first: scroll once its cards are there.
        val androidx = hasTestTag(LicencesTestTags.project(ANDROIDX))
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(LicencesTestTags.LIST).performScrollToNode(androidx) }.isSuccess
        }
        compose.onNode(androidx).performClick()
        waitFor(hasTestTag(LicencesTestTags.component(ROOM)))

        val okhttp = hasTestTag(LicencesTestTags.project(OKHTTP))
        compose.onNodeWithTag(LicencesTestTags.LIST).performScrollToNode(okhttp)
        compose.onNode(okhttp).performClick()
        waitFor(inDialog(hasText("Apache License", substring = true)))
        compose.onNodeWithTag(LicencesTestTags.CLOSE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LicencesTestTags.DIALOG), TIMEOUT_MS)
        back()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
    }

    private fun submitAndConfirm() {
        waitFor(hasTestTag(ChangePasswordTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(ChangePasswordTestTags.SUBMIT).performScrollTo().performClick()
        waitFor(hasText("Change the password?"))
        compose.onAllNodes(hasText("Change password") and hasClickAction()).onLast().performClick()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(text)
    }

    private fun signIn() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        type(SignInTestTags.ADDRESS, MockServer.baseUrl.toString().removeSuffix("/"))
        type(SignInTestTags.PASSWORD, MockServer.PASSWORD)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
    }

    private fun openSettings() {
        val settings = hasContentDescription("Settings") and hasClickAction()
        waitFor(settings)
        compose.onNode(settings).performClick()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    /** A node of the licence dialog, which is its own window: its texts sit below the tagged card. */
    private fun inDialog(matcher: SemanticsMatcher) = matcher and hasAnyAncestor(hasTestTag(LicencesTestTags.DIALOG))

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val OFFLINE = "offline"
        const val ANDROIDX = "AndroidX and Jetpack Compose"
        const val ROOM = "androidx.room:room-runtime"
        const val OKHTTP = "okhttp"
        const val NEW_PASSWORD = "a brand new password"
        const val TIMEOUT_MS = 30_000L
    }
}
