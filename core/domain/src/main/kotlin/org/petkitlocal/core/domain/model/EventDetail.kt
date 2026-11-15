package org.petkitlocal.core.domain.model

import java.time.Instant

/**
 * One stored event, as `GET /timeline/{event_id}` decodes it. [fields] carry the server's English
 * label and text; screens translate them by [EventField.key].
 */
data class EventDetail(
    val id: Long,
    val deviceId: Long?,
    val deviceType: String?,
    val ts: Instant?,
    val eventType: String?,
    val eventKind: String?,
    /** The server's English label. */
    val label: String,
    val pet: PetRef?,
    val fields: List<EventField>,
    val media: TimelineMedia?,
)

/** One decoded field of an event: its stable [key], and the server's English [label] and [text]. */
data class EventField(val key: String, val label: String, val text: String)
