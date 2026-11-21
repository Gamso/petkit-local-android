package org.petkitlocal.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless Home screen: content per state, clicks, labels. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var refreshes = 0

    private val navigation = HomeNavigation(
        onDeviceClick = { opened += "device:$it" },
        onOpenPets = { opened += "pets" },
        onOpenPet = { opened += "pet:$it" },
        onOpenHistory = { opened += "history" },
        onOpenSettings = { opened += "settings" },
    )

    private fun show(state: HomeUiState) {
        compose.setContent { PkTheme { HomeScreen(state, onRefresh = { refreshes++ }, navigation = navigation) } }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `the notification banner says why, and each answer reaches its callback`() {
        val answers = mutableListOf<String>()
        compose.setContent {
            PkTheme {
                HomeScreen(
                    HomeFixtures.notificationsBlocked,
                    onRefresh = {},
                    navigation = navigation,
                    notificationBanner = NotificationBannerActions(
                        onAllow = { answers += "allow" },
                        onNotNow = { answers += "not now" },
                    ),
                )
            }
        }

        compose.onNodeWithText("none can show until Android allows them", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(HomeTestTags.NOTIFICATION_ALLOW).assertHasClickAction().performClick()
        compose.onNodeWithTag(HomeTestTags.NOTIFICATION_NOT_NOW).assertHasClickAction().performClick()
        assertEquals(listOf("allow", "not now"), answers)
    }

    @Test
    fun `no banner when nothing is to be asked`() {
        show(HomeFixtures.content)
        compose.onNodeWithTag(HomeTestTags.NOTIFICATION_BANNER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+fr")
    fun `the notification banner in French`() {
        show(HomeFixtures.notificationsBlocked)
        compose.onNodeWithText("Autoriser").assertExists()
        compose.onNodeWithText("Plus tard").assertExists()
    }

    @Test
    fun `each family shows its headline figure and its status`() {
        show(HomeFixtures.content)

        // Pura Max: three visits today, idle.
        compose.onNodeWithTag(HomeTestTags.device(1002)).assertIsDisplayed()
        // Two litter boxes, so two of these.
        compose.onAllNodesWithText("Visits today", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Idle", useUnmergedTree = true).onFirst().assertExists()
        // T5: cleaning, from its work mode.
        compose.onNodeWithText("Cleaning", useUnmergedTree = true).assertExists()
        // D4: what it served today, and how many meals.
        scrollTo("Served today")
        compose.onNodeWithText("40 g", useUnmergedTree = true).assertExists()
        // Both feeders served one meal.
        compose.onAllNodesWithText("1 meal", useUnmergedTree = true).assertCountEquals(2)
        // The fountains: drinks for the W7H, water for the others, offline for the W5.
        scrollTo("Drinks today")
        scrollTo("EverSweet Max 2")
        compose.onNodeWithText("Offline", useUnmergedTree = true).assertExists()
        // The unknown kind. (The Pura Air has no card: its level is on its litter box's page.)
        scrollTo("Nothing to report")
    }

    @Test
    fun `a Pura Air shows nowhere on Home, a Bluetooth fountain's card says its relay`() {
        show(HomeFixtures.content)

        // No card of its own, and nothing of it on the Pura Max's: its level is on the box's page.
        compose.onNodeWithTag(HomeTestTags.device(4001)).assertDoesNotExist()
        compose.onNodeWithText("Pura Air", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("63 %", useUnmergedTree = true).assertDoesNotExist()
        // The CTW3's card is its own, and names the feeder relaying it; the card opens the fountain.
        scrollTo("via Fresh Element Solo")
        compose.onNodeWithTag(HomeTestTags.device(3003)).performClick()
        // The W5 is linked to no device: no relay to name.
        compose.onNodeWithText("via", substring = true, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("via ", substring = true, useUnmergedTree = true).assertCountEquals(1)

        assertEquals(listOf("device:3003"), opened)
    }

    @Test
    fun `the most urgent alerts come first, and the rest are one tap away`() {
        show(HomeFixtures.content)

        compose.onNodeWithText("Error: motor stuck · YumShare Dual-Hopper").assertExists()
        compose.onNodeWithText("Offline · EverSweet 3 Pro").assertExists()
        compose.onNodeWithText("Waste Bin Full · Pura Max").assertExists()
        compose.onNodeWithText("Hopper 2 empty", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Show 3 more alerts").performClick()

        compose.onNodeWithText("Hopper 2 empty · YumShare Dual-Hopper").assertExists()
        compose.onNodeWithText("Desiccant: 3 days left · Fresh Element Solo").assertExists()
        compose.onNodeWithText("Filter low: 12 % · EverSweet Max 2").assertExists()
    }

    @Test
    fun `cards, alerts, pets and the header lead where they should`() {
        show(HomeFixtures.content)

        compose.onNodeWithTag(HomeTestTags.device(1002)).performClick()
        compose.onNodeWithText("Offline · EverSweet 3 Pro").performClick()
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        scrollTo("Mochi")
        compose.onNodeWithText("Mochi", useUnmergedTree = true).performClick()
        compose.onNodeWithText("See all").performClick()

        assertEquals(listOf("device:1002", "device:3002", "history", "settings", "pet:1", "pets"), opened)
    }

    @Test
    fun `pets show their weight and today's visits`() {
        show(HomeFixtures.content)
        scrollTo("Tigrou")

        compose.onNodeWithText("4.3${NBSP}kg · 2 visits", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("6.0${NBSP}kg · 2 visits", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the day strip tells TalkBack how many events it holds`() {
        show(HomeFixtures.content)

        // Pura Max: three visits and the night's cleaning.
        compose.onNodeWithContentDescription("4 events today", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `offline, the last known data stays under a banner`() {
        show(HomeFixtures.offline)

        compose.onNodeWithText("Server unreachable. Showing the last known data.").assertExists()
        compose.onNodeWithTag(HomeTestTags.device(1002)).assertExists()
    }

    @Test
    fun `with nothing cached and no server, the error offers a retry`() {
        show(HomeFixtures.unreachableEmpty)

        compose.onNodeWithText("The server cannot be reached.").assertExists()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, refreshes)
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h2000dp")
    fun `the screen speaks French`() {
        show(HomeFixtures.content)

        compose.onNodeWithText("Ma maison").assertExists()
        compose.onNodeWithText("Bac à déchets plein · Pura Max").assertExists()
        compose.onAllNodesWithText("Visites aujourd'hui", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Au repos", useUnmergedTree = true).onFirst().assertExists()
        compose.onNodeWithText("Nettoyage", useUnmergedTree = true).assertExists()
        scrollTo("Mochi")
        compose.onNodeWithText("4,3${NBSP}kg · 2 visites", useUnmergedTree = true).assertExists()
    }

    private companion object {
        /** formatWeightKg keeps the number and its unit together. */
        const val NBSP = '\u00A0'
    }
}
