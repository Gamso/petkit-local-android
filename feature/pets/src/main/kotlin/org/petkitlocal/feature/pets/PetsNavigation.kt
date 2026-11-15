package org.petkitlocal.feature.pets

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PetsDestination

fun NavController.navigateToPets() = navigate(PetsDestination)

fun NavGraphBuilder.petsScreen(onBack: () -> Unit) {
    composable<PetsDestination> { PetsRoute(onBack = onBack) }
}
