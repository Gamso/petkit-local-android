package org.petkitlocal.core.designsystem.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI tests (Robolectric): clicks reach their callbacks, and TalkBack hears the state. */
@RunWith(RobolectricTestRunner::class)
class ComponentBehaviourTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the back button is read as Back and reaches its callback`() {
        var clicks = 0
        compose.setContent { PkTheme { PkBackButton(onClick = { clicks++ }) } }

        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, clicks)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the back button is read as Retour in French`() {
        compose.setContent { PkTheme { PkBackButton(onClick = {}) } }
        compose.onNodeWithContentDescription("Retour").assertExists()
    }

    @Test
    fun `confirm dialog sends confirm and dismiss to their callbacks`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            PkTheme {
                PkConfirmDialog(
                    title = "Empty the litter?",
                    message = "This cannot be undone.",
                    confirmLabel = "Empty",
                    onConfirm = { calls += "confirm" },
                    onDismiss = { calls += "dismiss" },
                )
            }
        }

        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Empty").performClick()

        assertEquals(listOf("dismiss", "confirm"), calls)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the dialog's default dismiss label is translated`() {
        compose.setContent {
            PkTheme { PkConfirmDialogContent("T", "M", confirmLabel = "Vider", onConfirm = {}, onDismiss = {}) }
        }
        compose.onNodeWithText("Annuler").assertExists()
    }

    @Test
    fun `a filter chip is a selected radio button for accessibility`() {
        compose.setContent { PkTheme { PkFilterChip("Visits", selected = true, onClick = {}) } }

        compose.onNodeWithText("Visits", useUnmergedTree = false)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `a mode chip states its value in words and toggles`() {
        var toggledTo: Boolean? = null
        compose.setContent { PkTheme { PkModeChip("Auto clean", on = false, onToggle = { toggledTo = it }) } }

        compose.onNodeWithText("Auto clean · Off").assertIsOff().performClick()

        assertEquals(true, toggledTo)
    }

    @Test
    fun `an action sheet button reports its key`() {
        var pressed: String? = null
        compose.setContent {
            PkTheme {
                PkActionSheet(
                    actions = listOf(
                        PkSheetAction(
                            "clean",
                            "Clean",
                            org.petkitlocal.core.designsystem.icon.PkIcons.Clean,
                            primary = true,
                        ),
                        PkSheetAction("level", "Level", org.petkitlocal.core.designsystem.icon.PkIcons.Level),
                    ),
                    onAction = { pressed = it },
                )
            }
        }

        compose.onNodeWithText("Level").performClick()

        assertEquals("level", pressed)
    }

    @Test
    fun `the error view retries`() {
        var retried = false
        compose.setContent { PkTheme { PkErrorView("Failed", onRetry = { retried = true }) } }

        compose.onNodeWithText("Retry").performClick()

        assertEquals(true, retried)
    }
}
