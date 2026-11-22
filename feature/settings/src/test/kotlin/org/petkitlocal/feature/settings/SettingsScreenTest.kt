// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.settings.NotificationKinds
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests (Robolectric): what each section says, and where each tap goes. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = SettingsActions(
        onBack = { calls += "back" },
        onRetry = { calls += "retry" },
        onKindToggle = { kind, on -> calls += "$kind=$on" },
        onNotificationsToggle = { on -> calls += "notifications=$on" },
        onAllowNotifications = { calls += "allow notifications" },
        onAllowBackground = { calls += "allow background" },
        onBatteryConfirm = { calls += "battery continue" },
        onBatteryDismiss = { calls += "battery not now" },
        onChangePassword = { calls += "password" },
        onOpenLicences = { calls += "licences" },
        onAppLicenceClick = { calls += "app licence" },
        onSignOutClick = { calls += "sign out" },
        onSignOutConfirm = { calls += "confirm" },
        onSignOutDismiss = { calls += "dismiss" },
        onAppLicenceDismiss = { calls += "app licence dismissed" },
    )

    private fun show(state: SettingsUiState) = compose.setContent {
        PkTheme { CompositionLocalProvider(LocalInspectionMode provides true) { SettingsScreen(state, actions) } }
    }

    @Test
    fun `every intent reaches its callback`() {
        show(SettingsPreviewData.loaded)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag(SettingsTestTags.kind(NotificationKinds.FEED_SERVED)).performScrollTo().performClick()
        compose.onNodeWithTag(SettingsTestTags.kind(NotificationKinds.TOILET)).performScrollTo().performClick()
        compose.onNodeWithTag(SettingsTestTags.CHANGE_PASSWORD).performScrollTo().performClick()
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onNodeWithTag(SettingsTestTags.APP_LICENCE).performScrollTo().performClick()
        compose.onNodeWithTag(SettingsTestTags.LICENCES).performScrollTo().performClick()

        val expected =
            listOf("back", "feed_served=true", "toilet=false", "password", "sign out", "app licence", "licences")
        assertEquals(expected, calls)
    }

    @Test
    fun `the notifications switch, and what Android must allow, each with its button`() {
        show(SettingsPreviewData.androidInTheWay)

        compose.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_SWITCH).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("does not let Patoune show notifications", substring = true).assertExists()
        compose.onNodeWithTag(SettingsTestTags.ALLOW_NOTIFICATIONS).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag(SettingsTestTags.ALLOW_BACKGROUND).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf("notifications=false", "allow notifications", "allow background"), calls)
    }

    @Test
    fun `notifications off, Android is asked for nothing`() {
        show(SettingsPreviewData.notificationsOff)

        compose.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_SWITCH).performScrollTo().assertIsOff()
        compose.onNodeWithTag(SettingsTestTags.ALLOW_NOTIFICATIONS).assertDoesNotExist()
        compose.onNodeWithTag(SettingsTestTags.ALLOW_BACKGROUND).assertDoesNotExist()
    }

    @Test
    fun `the battery is explained before Android asks`() {
        show(SettingsPreviewData.androidInTheWay.copy(explainingBattery = true))

        compose.onNodeWithText("Let alerts arrive on time").assertExists()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithTag(SettingsTestTags.BATTERY_CONTINUE).performClick()
        assertEquals(listOf("battery not now", "battery continue"), calls)
    }

    @Test
    fun `the server's details and this phone's name are shown`() {
        show(SettingsPreviewData.loaded)

        compose.onNodeWithText("https://pets.example.com").assertExists()
        compose.onNodeWithText("1.4.0").assertExists()
        compose.onNodeWithText("Google Pixel 8").performScrollTo()
        compose.onNodeWithText("0.1.0").performScrollTo()
        compose.onNodeWithText("not affiliated with", substring = true).performScrollTo()
    }

    @Test
    fun `each switch shows the server's choice, and a kind off in the panel says so`() {
        show(SettingsPreviewData.loaded)

        compose.onNodeWithTag(SettingsTestTags.kind(NotificationKinds.TOILET)).assertIsOn()
        compose.onNodeWithTag(SettingsTestTags.kind(NotificationKinds.FEED_SERVED)).assertIsOff()
        compose.onNodeWithText("Turned off in the panel", substring = true).assertExists()
    }

    @Test
    fun `while a kind is saved, the switches wait`() {
        show(SettingsPreviewData.saving)
        compose.onNodeWithTag(SettingsTestTags.kind(NotificationKinds.ERROR)).assertIsNotEnabled()
    }

    @Test
    fun `an unreachable server is said where it matters, with a retry`() {
        show(SettingsPreviewData.unreachable)

        compose.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_ERROR).performScrollTo()
        compose.onNodeWithText("notification choices cannot be shown", substring = true).assertExists()
    }

    @Test
    fun `the app's licence shows its name, and its text in a dialog`() {
        show(SettingsPreviewData.loaded.copy(showingAppLicence = true))

        compose.onNodeWithText("Copyright (c) 2026 Gamso", substring = true).assertExists()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf("app licence dismissed"), calls)
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
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the screen is in French`() {
        show(SettingsPreviewData.loaded)
        compose.onNodeWithText("Réglages").assertExists()
        compose.onNodeWithText("Passages à la litière").assertExists()
        compose.onNodeWithText("Notifications sur ce téléphone").assertExists()
        compose.onNodeWithContentDescription("Retour").assertExists()
    }
}

/**
 * Settings in light/dark x EN/FR: everything read (one kind off here, one off in the panel), a
 * kind being saved (its indicator held still by inspection mode, or the capture hangs), and the
 * server out of reach. Tall enough for the whole page. SDK 36: see core:designsystem.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2050dp-xhdpi", sdk = [36])
class SettingsScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun settings() = capture("settings") { SettingsScreen(SettingsPreviewData.loaded, SettingsActions()) }

    @Test
    fun saving() = capture("settings_saving") { SettingsScreen(SettingsPreviewData.saving, SettingsActions()) }

    @Test
    fun unreachable() = capture("settings_unreachable") {
        SettingsScreen(SettingsPreviewData.unreachable, SettingsActions())
    }

    @Test
    fun androidInTheWay() = capture("settings_android_in_the_way") {
        SettingsScreen(SettingsPreviewData.androidInTheWay, SettingsActions())
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { CompositionLocalProvider(LocalInspectionMode provides true) { content() } }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.screens
    }
}

/** The battery explanation, as it sits over the screen, in light/dark x EN/FR. SDK 36: see core:designsystem. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [36])
class BatteryDialogScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun batteryDialog() {
        captureRoboImage(
            filePath = "src/test/screenshots/settings_battery_dialog_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme {
                // The dialog's card over the screen; a real window does not render here.
                Box(
                    Modifier.fillMaxSize().background(PkTheme.colors.background).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BatteryDialogContent(onConfirm = {}, onDismiss = {})
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.screens
    }
}
