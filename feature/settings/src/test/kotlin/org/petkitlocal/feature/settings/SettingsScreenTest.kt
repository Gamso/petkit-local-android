// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests (Robolectric): the way back and the guarded sign-out. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = SettingsActions(
        onBack = { calls += "back" },
        onSignOutClick = { calls += "sign out" },
        onSignOutConfirm = { calls += "confirm" },
        onSignOutDismiss = { calls += "dismiss" },
    )

    private fun show(state: SettingsUiState, actions: SettingsActions = this.actions) =
        compose.setContent { PkTheme { SettingsScreen(state, actions) } }

    @Test
    fun `every intent reaches its callback`() {
        show(SettingsUiState(server = "https://pets.example.com"))

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()

        assertEquals(listOf("back", "sign out"), calls)
        compose.onNodeWithText("Signed in to https://pets.example.com").assertExists()
    }

    @Test
    fun `signing out is confirmed in a dialog`() {
        show(SettingsUiState(confirmingSignOut = true))

        compose.onNodeWithText("Sign out of this phone?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `while signing out the button waits`() {
        show(SettingsUiState(signingOut = true))
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the screen is in French`() {
        show(SettingsUiState(server = "https://pets.example.com"))
        compose.onNodeWithText("Réglages").assertExists()
        compose.onNodeWithText("Se déconnecter").assertExists()
        compose.onNodeWithContentDescription("Retour").assertExists()
    }
}

/** Settings in light/dark x EN/FR. SDK 36: see core:designsystem's ComponentScreenshotTest. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [36])
class SettingsScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun settings() = capture("settings") {
        SettingsScreen(
            SettingsUiState(server = "https://pets.example.com"),
            SettingsActions(),
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { content() }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.screens
    }
}
