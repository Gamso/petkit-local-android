package org.petkitlocal.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.feature.home.R as HomeR

/**
 * End to end, on an emulator: the debug app starts through its real Application (Hilt) and
 * MainActivity, and draws the first screen. Until sign-in exists (M1) that is Home.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppStartsOnHome() {
        val title = compose.activity.getString(HomeR.string.home_title)

        compose.onNode(hasText(title) and isHeading()).assertIsDisplayed()
    }
}
