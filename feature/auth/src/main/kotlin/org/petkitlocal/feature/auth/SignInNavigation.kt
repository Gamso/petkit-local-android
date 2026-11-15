package org.petkitlocal.feature.auth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SignInDestination

fun NavController.navigateToSignIn() = navigate(SignInDestination)

fun NavGraphBuilder.signInScreen(onBack: () -> Unit) {
    composable<SignInDestination> { SignInRoute(onBack = onBack) }
}
