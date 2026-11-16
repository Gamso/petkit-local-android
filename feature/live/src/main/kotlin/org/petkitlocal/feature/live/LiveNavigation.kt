package org.petkitlocal.feature.live

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The live video screen of one camera (SPEC.md §5.7). */
@Serializable
data class LiveDestination(val deviceId: Long)

fun NavController.navigateToLive(deviceId: Long) = navigate(LiveDestination(deviceId))

fun NavGraphBuilder.liveScreen(onBack: () -> Unit) {
    composable<LiveDestination> { LiveRoute(onBack = onBack) }
}
