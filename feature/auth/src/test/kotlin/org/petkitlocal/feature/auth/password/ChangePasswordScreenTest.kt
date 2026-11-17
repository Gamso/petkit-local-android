// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.auth.password

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.ChangePasswordFailure
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests (Robolectric): errors in place, the confirmation, the result. */
@RunWith(RobolectricTestRunner::class)
class ChangePasswordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = ChangePasswordActions(
        onBack = { calls += "back" },
        onCurrentChange = { calls += "current:$it" },
        onNewChange = { calls += "new:$it" },
        onRepeatedChange = { calls += "repeated:$it" },
        onToggleVisibility = { calls += "eye" },
        onSubmit = { calls += "submit" },
        onConfirm = { calls += "confirm" },
        onDismissConfirm = { calls += "dismiss" },
    )

    private fun show(state: ChangePasswordUiState) =
        compose.setContent { PkTheme { ChangePasswordScreen(state, actions) } }

    @Test
    fun `typing and pressing reach their callbacks, and the button is never greyed out for a missing field`() {
        show(ChangePasswordUiState())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag(ChangePasswordTestTags.CURRENT).performTextInput("demo")
        compose.onNodeWithTag(ChangePasswordTestTags.NEW).performTextInput("x")
        compose.onNodeWithTag(ChangePasswordTestTags.REPEAT).performTextInput("y")
        compose.onNodeWithTag(ChangePasswordTestTags.SUBMIT).performScrollTo().assertIsEnabled().performClick()

        // The fields are the state's: each keystroke is reported, and the unchanged state puts them back.
        val reported = calls.filterNot { it.endsWith(":") }
        assertEquals(listOf("back", "current:demo", "new:x", "repeated:y", "submit"), reported)
    }

    @Test
    fun `each error is said under its own field`() {
        show(ChangePasswordUiState(failure = ChangePasswordFailure.Mismatch))
        compose.onNodeWithText("This is not the same as the new password.").assertExists()
        compose.onNodeWithText("At least 8 characters.").assertExists()
    }

    @Test
    fun `a wrong current password locks the button for the server's wait`() {
        show(ChangePasswordUiState(failure = ChangePasswordFailure.WrongCurrent(3.seconds), lockedSeconds = 3))
        compose.onNodeWithText("Wrong password.").assertExists()
        compose.onNodeWithTag(ChangePasswordTestTags.SUBMIT).assertIsNotEnabled()
        compose.onNodeWithText("Try again in 3 seconds").assertExists()
    }

    @Test
    fun `the change is confirmed in a dialog`() {
        show(ChangePasswordUiState(confirming = true))
        compose.onNodeWithText("Change the password?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `once changed it says how many phones were signed out, and goes back`() {
        show(ChangePasswordUiState(signedOutPhones = 2))
        compose.onNodeWithText("2 other phones were signed out", substring = true).assertExists()
        compose.onNodeWithTag(ChangePasswordTestTags.DONE).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the screen is in French`() {
        show(ChangePasswordUiState(failure = ChangePasswordFailure.CurrentMissing))
        compose.onNodeWithText("Saisissez le mot de passe actuel.").assertExists()
        compose.onNodeWithContentDescription("Retour").assertExists()
    }
}

/** The form with errors, and the result, in light/dark x EN/FR. SDK 36: see core:designsystem. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [36])
class ChangePasswordScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun errors() = capture("change_password_errors") {
        ChangePasswordScreen(
            ChangePasswordUiState(
                new = "a new password",
                repeated = "a new password",
                failure = ChangePasswordFailure.WrongCurrent(4.seconds),
                lockedSeconds = 4,
            ),
            ChangePasswordActions(),
        )
    }

    @Test
    fun changed() = capture("change_password_done") {
        ChangePasswordScreen(ChangePasswordUiState(signedOutPhones = 2), ChangePasswordActions())
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
