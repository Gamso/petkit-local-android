package org.petkitlocal.feature.auth.password

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Changing the server's password, opened from Settings. */
@Serializable
data object ChangePasswordDestination

fun NavController.navigateToChangePassword() = navigate(ChangePasswordDestination)

fun NavGraphBuilder.changePasswordScreen(onBack: () -> Unit) {
    composable<ChangePasswordDestination> { ChangePasswordRoute(onBack = onBack) }
}
