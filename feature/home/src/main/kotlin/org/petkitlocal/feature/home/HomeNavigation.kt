package org.petkitlocal.feature.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

fun NavController.navigateToHome() = navigate(HomeDestination)

fun NavGraphBuilder.homeScreen(navigation: HomeNavigation) {
    composable<HomeDestination> { HomeRoute(navigation = navigation) }
}
