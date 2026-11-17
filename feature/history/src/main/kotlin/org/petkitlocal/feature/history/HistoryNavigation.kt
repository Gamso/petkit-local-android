package org.petkitlocal.feature.history

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.time.LocalDate
import kotlinx.serialization.Serializable
import org.petkitlocal.core.domain.model.MediaFile

/**
 * History (SPEC.md §5.4): every device's cards, or one device's when opened from its page, or one
 * pet's when opened from the pet's page; on today, or on [day] (`YYYY-MM-DD`) when it is given.
 */
@Serializable
data class HistoryDestination(val deviceId: Long? = null, val petId: Long? = null, val day: String? = null)

/**
 * One clip in History's clip player, opened from outside History: a camera's recent clips, on its
 * page and under its live video. [url] is the clip's address on the server (fetched with the
 * bearer header, never a token in it); [kind] the name of its `MediaKind`, which titles it.
 */
@Serializable
data class ClipDestination(val url: String, val kind: String)

fun NavController.navigateToHistory(deviceId: Long? = null, petId: Long? = null, day: LocalDate? = null) =
    navigate(HistoryDestination(deviceId, petId, day?.toString()))

fun NavController.navigateToClip(clip: MediaFile) = navigate(ClipDestination(clip.url, clip.kind.name))

fun NavGraphBuilder.historyScreen(onBack: () -> Unit) {
    composable<HistoryDestination> { HistoryRoute(onBack = onBack) }
}

fun NavGraphBuilder.clipScreen(onBack: () -> Unit) {
    composable<ClipDestination> { ClipRoute(onBack = onBack) }
}

/** The [HistoryDestination] properties the arguments are stored under. */
internal const val DEVICE_ID_KEY = "deviceId"
internal const val PET_ID_KEY = "petId"
internal const val DAY_KEY = "day"

/** The [ClipDestination] properties the arguments are stored under. */
internal const val CLIP_URL_KEY = "url"
internal const val CLIP_KIND_KEY = "kind"
