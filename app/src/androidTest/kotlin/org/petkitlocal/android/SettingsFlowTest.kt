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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.auth.password.ChangePasswordTestTags
import org.petkitlocal.feature.settings.SettingsTestTags
import org.petkitlocal.feature.settings.licences.LicencesTestTags

/**
 * Settings end to end (issue #24), on a device, against the mock server or the real one
 * (core:e2e): the server's version read from it, a notification kind turned off and held by the
 * server for this session, the password changed after a wrong current one, the app's licence and
 * the open-source licences read from the APK, then a confirmed sign-out back to sign-in. The
 * server is reset before each test, which also puts the password back.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsReadTheServerChangeItsChoicesAndSignOut() {
        // The version the server gives a signed-in client, read by a session closed again at once.
        val version = runBlocking {
            val api = TestServer.signedInApi()
            api.getInfo().serverVersion.also { api.logout() }
        }
        signIn()
        openSettings()

        // The server's version from GET /info. Each row is one merged node.
        waitFor(hasTestTag(SettingsTestTags.SERVER_VERSION) and hasText(checkNotNull(version)))

        // A kind turned off moves once the server holds it, and the server holds it for this phone.
        val offline = hasTestTag(SettingsTestTags.kind(OFFLINE))
        waitFor(offline and isOn())
        compose.onNode(offline).performScrollTo().performClick()
        waitFor(offline and isOff() and isEnabled())
        val kinds = phone().getJSONArray("kinds").let { kinds -> List(kinds.length()) { kinds.getString(it) } }
        assertTrue(kinds.toString(), TOILET in kinds)
        assertFalse(kinds.toString(), OFFLINE in kinds)

        changePassword()
        readLicences()

        // Signing out is confirmed, and lands on sign-in.
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onLast().performClick()
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        assertEquals(0, TestServer.sessions().size)
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

        type(ChangePasswordTestTags.CURRENT, TestServer.password)
        // The server's lockout after a wrong password (1 s at first) holds the button meanwhile.
        submitAndConfirm()
        waitFor(hasTestTag(ChangePasswordTestTags.DONE_CARD))
        compose.onNodeWithTag(ChangePasswordTestTags.DONE).performScrollTo().performClick()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
        // Still signed in: the new password is the server's now.
        phone()
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

    /** The server's one session: this phone's. */
    private fun phone(): JSONObject = TestServer.sessions().single()

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
        type(SignInTestTags.ADDRESS, TestServer.address)
        type(SignInTestTags.PASSWORD, TestServer.password)
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
        const val TOILET = "toilet"
        const val ANDROIDX = "AndroidX and Jetpack Compose"
        const val ROOM = "androidx.room:room-runtime"
        const val OKHTTP = "okhttp"
        const val NEW_PASSWORD = "a brand new password"
        const val TIMEOUT_MS = 30_000L
    }
}
