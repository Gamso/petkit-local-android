package org.petkitlocal.android.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's way back (the back arrow of every page, `navigateBack`) against taps faster than the
 * page transition: the frame clock is held, so a second tap lands while the page being left is
 * still on screen and its back arrow still takes taps, as on a phone. A second pop there used to
 * take the start page off too, leaving the NavHost nothing to draw: an empty page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavigateBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    @Test
    fun twoQuickTapsOnTheBackArrowGoBackOnePageAndNeverEmptyTheStack() {
        showGraph()
        navigate(PageA)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Back from A").performClick()
        compose.onNodeWithText("Back from A").performClick()
        compose.mainClock.autoAdvance = true

        compose.onNodeWithText("Home page").assertIsDisplayed()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<Home>() == true)
    }

    @Test
    fun twoQuickTapsTwoPagesDeepStopAtThePageBefore() {
        showGraph()
        navigate(PageA)
        navigate(PageB)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Back from B").performClick()
        compose.onNodeWithText("Back from B").performClick()
        compose.mainClock.autoAdvance = true

        compose.onNodeWithText("Back from A").assertIsDisplayed()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<PageA>() == true)
    }

    @Test
    fun twoQuickSystemBacksLandOnTheStartPage() {
        showGraph()
        navigate(PageA)

        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.mainClock.autoAdvance = true

        compose.onNodeWithText("Home page").assertIsDisplayed()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<Home>() == true)
    }

    @Test
    fun backOnTheStartPageKeepsIt() {
        showGraph()

        compose.runOnUiThread { navController.navigateBack() }

        compose.onNodeWithText("Home page").assertIsDisplayed()
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<Home>() == true)
    }

    private fun showGraph() {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Home) {
                composable<Home> { Text("Home page") }
                composable<PageA> { Button(onClick = { navController.navigateBack() }) { Text("Back from A") } }
                composable<PageB> { Button(onClick = { navController.navigateBack() }) { Text("Back from B") } }
            }
        }
    }

    private fun navigate(route: Any) {
        compose.runOnUiThread { navController.navigate(route) }
        compose.waitForIdle()
    }

    // Navigation finds a route's serializer by reflection: the routes cannot be private.
    @Serializable internal data object Home

    @Serializable internal data object PageA

    @Serializable internal data object PageB
}
