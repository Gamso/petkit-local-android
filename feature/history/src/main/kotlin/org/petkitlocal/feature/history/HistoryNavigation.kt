package org.petkitlocal.feature.history

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * History (SPEC.md §5.4): every device's cards, or one device's when opened from its page, or one
 * pet's when opened from the pet's page.
 */
@Serializable
data class HistoryDestination(val deviceId: Long? = null, val petId: Long? = null)

fun NavController.navigateToHistory(deviceId: Long? = null, petId: Long? = null) =
    navigate(HistoryDestination(deviceId, petId))

fun NavGraphBuilder.historyScreen(onBack: () -> Unit) {
    composable<HistoryDestination> { HistoryRoute(onBack = onBack) }
}

/** The [HistoryDestination] properties the arguments are stored under. */
internal const val DEVICE_ID_KEY = "deviceId"
internal const val PET_ID_KEY = "petId"
