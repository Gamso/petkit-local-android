package org.petkitlocal.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.feature.auth.SignInTestTags

/**
 * End to end, on an emulator: the debug app starts through its real Application (Hilt) and
 * MainActivity, and draws the first screen. Signed out, as on a fresh install, that is sign-in.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppStartsOnSignIn() {
        compose.onNodeWithTag(SignInTestTags.SCREEN).assertIsDisplayed()
    }
}
