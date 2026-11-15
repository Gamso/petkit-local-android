package org.petkitlocal.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * The sign-in screen. Nothing navigates to it by hand: the app's auth gate shows it whenever
 * there is no session, and leaves it for Home when one starts.
 */
@Serializable
data object SignInDestination

fun NavGraphBuilder.signInScreen() {
    composable<SignInDestination> { SignInRoute() }
}
