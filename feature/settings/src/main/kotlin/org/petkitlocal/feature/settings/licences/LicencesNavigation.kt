package org.petkitlocal.feature.settings.licences

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The open-source components the app ships, from About. */
@Serializable
data object LicencesDestination

fun NavController.navigateToLicences() = navigate(LicencesDestination)

fun NavGraphBuilder.licencesScreen(onBack: () -> Unit) {
    composable<LicencesDestination> { LicencesRoute(onBack = onBack) }
}
