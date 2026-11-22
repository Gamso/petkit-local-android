package org.petkitlocal.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.petkitlocal.feature.auth.SignInTestTags

/**
 * End to end, on an emulator: the debug app starts through its real Application (Hilt) and
 * MainActivity, and draws the first screen. Signed out, as on a fresh install, that is sign-in.
 *
 * The tests share the app's process, so the app is signed out first ([PetkitAppUnderTest]): the
 * test before this one may have ended signed in. No server is needed for that.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule(order = 0)
    val signedOut = object : ExternalResource() {
        override fun before() = PetkitAppUnderTest().signOut()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppStartsOnSignIn() {
        compose.onNodeWithTag(SignInTestTags.SCREEN).assertIsDisplayed()
    }
}
