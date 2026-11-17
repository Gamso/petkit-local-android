package org.petkitlocal.feature.live

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.petkitlocal.core.domain.model.MediaFile

/** The live video screen of one camera (SPEC.md §5.7). */
@Serializable
data class LiveDestination(val deviceId: Long)

fun NavController.navigateToLive(deviceId: Long) = navigate(LiveDestination(deviceId))

/**
 * [onOpenClip] plays one of the camera's recent clips; [onOpenHistory] shows History narrowed to
 * the camera, for the rest of them.
 */
fun NavGraphBuilder.liveScreen(
    onBack: () -> Unit,
    onOpenClip: (MediaFile) -> Unit,
    onOpenHistory: (deviceId: Long) -> Unit,
) {
    composable<LiveDestination> { LiveRoute(onBack, onOpenClip, onOpenHistory) }
}
