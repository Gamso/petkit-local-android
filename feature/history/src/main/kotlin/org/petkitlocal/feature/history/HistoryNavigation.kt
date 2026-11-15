package org.petkitlocal.feature.history

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HistoryDestination

fun NavController.navigateToHistory() = navigate(HistoryDestination)

fun NavGraphBuilder.historyScreen(onBack: () -> Unit) {
    composable<HistoryDestination> { HistoryRoute(onBack = onBack) }
}
