package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.EventDetail
import io.github.gamso.petkitlocal.mock.api.EventField
import io.github.gamso.petkitlocal.mock.api.MediaDto
import io.github.gamso.petkitlocal.mock.api.PetRef
import io.github.gamso.petkitlocal.mock.api.TimelineItemDto
import io.github.gamso.petkitlocal.mock.api.TimelineStep
import io.github.gamso.petkitlocal.mock.api.plainNumber
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** Event kinds, as `events/codes.py` names them. */
object Kinds {
    const val TOILET = "toilet_visit"
    const val CLEANING = "cleaning"
    const val PET = "pet"
    const val ERROR = "error"
    const val FEEDING = "feeding"
    const val DRINKING = "drinking"
    const val SYSTEM = "system"
}

/** The filter chips, in the panel's order (`events/sessions.py::FILTERS`). */
val FILTERS = listOf("pet", "toileting", "drinking", "feeding", "cleaning", "health_alert", "fault")

/** One stored device report: what `GET /timeline/{event_id}` returns. */
class EventRow(
    val id: Int,
    val deviceId: Int,
    val deviceType: String,
    val ts: Double,
    val eventType: String,
    val eventKind: String,
    val label: String,
    val petId: Int?,
    val fields: List<EventField>,
    val facts: List<String> = emptyList(),
    /** A low-level step the official app does not show. */
    val detail: Boolean = false,
)

/** An episode's media, as media-tree-relative paths. */
data class CardMedia(
    val snapshot: String? = null,
    val highlight: String? = null,
    val playback: String? = null,
    val timelapse: String? = null,
    val waste: List<String> = emptyList(),
    val health: List<String> = emptyList(),
    val videoPending: Boolean = false,
) {
    fun dto(): MediaDto {
        val thumbSource = snapshot ?: highlight ?: playback ?: timelapse
        return MediaDto(
            thumbnail = thumbSource?.let { "$API_PREFIX/media/thumb/$it" },
            snapshot = snapshot?.let(::mediaUrl),
            highlight = highlight?.let(::mediaUrl),
            playback = playback?.let(::mediaUrl),
            timelapse = timelapse?.let(::mediaUrl),
            waste = waste.map(::mediaUrl),
            health = health.map(::mediaUrl),
            videoPending = videoPending,
        )
    }

    val isEmpty get() =
        snapshot == null && highlight == null && playback == null && timelapse == null && waste.isEmpty() &&
            health.isEmpty()

    companion object {
        fun mediaUrl(rel: String) = "$API_PREFIX/media/$rel"
    }
}

const val API_PREFIX = "/app/v1"

/**
 * One timeline card: a grouped session (`events/sessions.py`). It is keyed by the row that
 * anchors it, and it changes while it lives: a cleaning attaches, a recording arrives.
 */
class Card(
    val id: Int,
    val kind: String,
    val deviceId: Int,
    val deviceType: String,
    var ts: Double,
    val displayTs: Double,
    val eventType: String,
    val eventKind: String,
    val label: String?,
    val petId: Int?,
    val durationSec: Int?,
    val weightG: Int?,
    val facts: List<String>,
    var media: CardMedia?,
    val steps: MutableList<Int> = mutableListOf(),
    val healthAlert: Boolean = false,
) {
    val filters: List<String>
        get() {
            val chips = mutableSetOf<String>()
            when (eventKind) {
                Kinds.TOILET -> chips += "toileting"
                Kinds.PET, "motion" -> chips += "pet"
                Kinds.ERROR -> chips += "fault"
                Kinds.FEEDING -> chips += "feeding"
                Kinds.DRINKING -> chips += "drinking"
                Kinds.CLEANING -> chips += "cleaning"
            }
            if (healthAlert) chips += "health_alert"
            return chips.sorted()
        }

    fun matches(kind: String): Boolean = kind == "all" || kind in filters
}

/** The stored history: rows by id, cards by id. Not thread-safe; [World] holds the lock. */
class Timeline {
    val rows = HashMap<Int, EventRow>()
    val cards = HashMap<Int, Card>()
    private var nextId = 1000

    fun newId(): Int = nextId++

    fun add(row: EventRow): EventRow {
        rows[row.id] = row
        return row
    }

    fun add(card: Card): Card {
        cards[card.id] = card
        return card
    }

    fun clear() {
        rows.clear()
        cards.clear()
        nextId = 1000
    }

    /** Newest first: `(ts, id)` descending, the cursor's sort key. */
    fun sorted(): List<Card> = cards.values.sortedWith(compareByDescending<Card> { it.ts }.thenByDescending { it.id })

    fun render(
        card: Card,
        pets: Map<Int, Pet>,
    ) = TimelineItemDto(
        id = card.id,
        kind = card.kind,
        filters = card.filters,
        deviceId = card.deviceId,
        deviceType = card.deviceType,
        ts = card.ts,
        displayTs = card.displayTs,
        eventType = card.eventType,
        eventKind = card.eventKind,
        label = if (card.kind == "visit") null else card.label,
        pet = petRef(pets, card.petId),
        durationSec = card.durationSec,
        weightG = card.weightG,
        served = null,
        facts = card.facts,
        media = card.media?.takeUnless { it.isEmpty }?.dto(),
        steps =
            card.steps.mapNotNull { rows[it] }.map { TimelineStep(it.id, it.ts, it.label, it.detail) },
    )

    fun detail(
        row: EventRow,
        pets: Map<Int, Pet>,
    ): EventDetail {
        val card = cards[row.id] ?: cards.values.firstOrNull { row.id in it.steps }
        val media = if (card?.id == row.id) card.media?.takeUnless { it.isEmpty }?.dto() else null
        return EventDetail(
            id = row.id,
            deviceId = row.deviceId,
            deviceType = row.deviceType,
            ts = row.ts,
            eventType = row.eventType,
            eventKind = row.eventKind,
            label = row.label,
            pet = petRef(pets, row.petId),
            facts = row.facts,
            fields = row.fields,
            media = media,
        )
    }

    companion object {
        fun petRef(
            pets: Map<Int, Pet>,
            id: Int?,
        ): PetRef? = id?.let { PetRef(it, pets[it]?.name) }

        /** An opaque continuation token: the sort key `(ts, id)` of the last card returned. */
        fun encodeCursor(
            ts: Double,
            id: Int,
        ): String {
            val raw = "[${plainNumber(ts)},$id]"
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        }

        /** The `(ts, id)` a cursor carries, or null for anything this server did not hand out. */
        fun decodeCursor(token: String): Pair<Double, Int>? =
            runCatching {
                val raw = Base64.getUrlDecoder().decode(token).decodeToString()
                val arr =
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(raw) as JsonArray
                require(arr.size == 2)
                val ts = arr[0].jsonPrimitive
                val id = arr[1].jsonPrimitive
                require(!ts.isString && !id.isString && ts.content != "true" && ts.content != "false")
                ts.doubleOrNull!! to id.int
            }.getOrNull()
    }
}
