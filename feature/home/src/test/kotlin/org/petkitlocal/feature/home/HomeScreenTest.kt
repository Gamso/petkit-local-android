package org.petkitlocal.feature.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless Home screen: content per state, clicks. */
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each shortcut reaches the callback`() {
        val opened = mutableListOf<HomeShortcut>()
        compose.setContent { PkTheme { HomeScreen(HomeUiState.Placeholder("M1"), onShortcut = { opened += it }) } }

        compose.onNodeWithText("History").performScrollTo().performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()

        assertEquals(listOf(HomeShortcut.History, HomeShortcut.Settings), opened)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the screen speaks French`() {
        compose.setContent { PkTheme { HomeScreen(HomeUiState.Placeholder("M1"), onShortcut = {}) } }

        compose.onNodeWithText("Accueil").assertExists()
        compose.onNodeWithText("Cet écran arrive avec le jalon M1.").assertExists()
    }
}
