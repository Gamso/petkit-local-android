package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Values of the `kind` filter of `GET /timeline`: the History filter chips. */
object TimelineFilter {
    const val ALL = "all"
    const val PET = "pet"
    const val TOILETING = "toileting"
    const val DRINKING = "drinking"
    const val FEEDING = "feeding"
    const val CLEANING = "cleaning"
    const val HEALTH_ALERT = "health_alert"
    const val FAULT = "fault"
    val KNOWN = setOf(ALL, PET, TOILETING, DRINKING, FEEDING, CLEANING, HEALTH_ALERT, FAULT)
}

/** `Media`: an episode's photos and videos, as API paths to fetch with the bearer header. */
@Serializable
data class MediaDto(
    @SerialName("thumbnail") val thumbnail: String?,
    @SerialName("snapshot") val snapshot: String?,
    /** The short event clip. */
    @SerialName("highlight") val highlight: String?,
    /** The whole recording, once assembled. */
    @SerialName("playback") val playback: String?,
    @SerialName("timelapse") val timelapse: String?,
    @SerialName("waste") val waste: List<String>,
    @SerialName("health") val health: List<String>,
    /** A recording is expected but still being assembled. */
    @SerialName("video_pending") val videoPending: Boolean,
)

/** `PetRef`. */
@Serializable
data class PetRefDto(
    @SerialName("id") val id: Long,
    /** Null when the pet has since been deleted. */
    @SerialName("name") val name: String?,
)

/** `TimelineStep`. */
@Serializable
data class TimelineStepDto(
    @SerialName("id") val id: Long,
    @SerialName("ts") val ts: Double?,
    @SerialName("label") val label: String,
    /** A low-level step the official app does not show. */
    @SerialName("detail") val detail: Boolean,
)

/** `TimelineItem`: one History card. */
@Serializable
data class TimelineItemDto(
    /** Pass to `GET /timeline/{event_id}`. */
    @SerialName("id") val id: Long,
    /** `visit`, `event`, or a grouped cycle kind. */
    @SerialName("kind") val kind: String,
    /** The filter chips this card falls under. */
    @SerialName("filters") val filters: List<String>,
    @SerialName("device_id") val deviceId: Long?,
    @SerialName("device_type") val deviceType: String?,
    /** Sort key: when the closing report arrived. */
    @SerialName("ts") val ts: Double?,
    /** What to show: e.g. when the pet entered. */
    @SerialName("display_ts") val displayTs: Double?,
    @SerialName("event_type") val eventType: String?,
    @SerialName("event_kind") val eventKind: String?,
    /** Null for a visit. */
    @SerialName("label") val label: String?,
    @SerialName("pet") val pet: PetRefDto?,
    @SerialName("duration_sec") val durationSec: Int?,
    @SerialName("weight_g") val weightG: Int?,
    /**
     * What a finished feed put out; null on every other card, and on a feed whose report states
     * no amount (which is not a meal that served nothing: that one is 0).
     */
    @SerialName("served") val served: ServedAmountsDto?,
    @SerialName("facts") val facts: List<String>,
    @SerialName("media") val media: MediaDto?,
    @SerialName("steps") val steps: List<TimelineStepDto>,
)

/**
 * `ServedAmounts`: a finished feed's output as the feeder reported it, in the model's own unit
 * (portions on a Dual-Hopper). A single hopper has no [hopper1] or [hopper2]; a Dual-Hopper's
 * [total] is their sum, and a hopper its report left out is null.
 */
@Serializable
data class ServedAmountsDto(
    @SerialName("total") val total: Int,
    @SerialName("hopper1") val hopper1: Int?,
    @SerialName("hopper2") val hopper2: Int?,
)

/** `TimelinePage`. */
@Serializable
data class TimelinePageDto(
    @SerialName("items") val items: List<TimelineItemDto>,
    /** Null on the last page. Opaque: pass it back verbatim. */
    @SerialName("next_cursor") val nextCursor: String?,
)

/** `EventField`: one decoded field of a stored event. */
@Serializable
data class EventFieldDto(
    @SerialName("key") val key: String,
    @SerialName("label") val label: String,
    @SerialName("text") val text: String,
)

/** `EventDetail`. */
@Serializable
data class EventDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("device_id") val deviceId: Long?,
    @SerialName("device_type") val deviceType: String?,
    @SerialName("ts") val ts: Double?,
    @SerialName("event_type") val eventType: String?,
    @SerialName("event_kind") val eventKind: String?,
    @SerialName("label") val label: String,
    @SerialName("pet") val pet: PetRefDto?,
    @SerialName("facts") val facts: List<String>,
    @SerialName("fields") val fields: List<EventFieldDto>,
    @SerialName("media") val media: MediaDto?,
)

/** Body of `GET /timeline/{event_id}`. */
@Serializable
data class EventResponseDto(@SerialName("event") val event: EventDetailDto)
