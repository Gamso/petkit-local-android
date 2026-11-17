package org.petkitlocal.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

fun NavController.navigateToSettings() = navigate(SettingsDestination)

/** Where Settings leads; the app wires each to another feature's screen. */
class SettingsNavigation(
    val onBack: () -> Unit = {},
    val onChangePassword: () -> Unit = {},
    val onOpenLicences: () -> Unit = {},
)

fun NavGraphBuilder.settingsScreen(navigation: SettingsNavigation) {
    composable<SettingsDestination> { SettingsRoute(navigation) }
}
