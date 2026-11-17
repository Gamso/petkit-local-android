package org.petkitlocal.core.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import org.petkitlocal.core.database.dao.TimelineDao
import org.petkitlocal.core.database.dao.TimelineFilter
import org.petkitlocal.core.database.dao.TimelineKey
import org.petkitlocal.core.database.model.TimelineItemRow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.EventField
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.domain.model.TimelineStep
import org.petkitlocal.core.domain.repository.TimelineRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.dto.EventDetailDto
import org.petkitlocal.core.network.dto.MediaDto
import org.petkitlocal.core.network.dto.TimelineItemDto
import org.petkitlocal.core.network.dto.TimelineStepDto
import org.petkitlocal.core.network.error.apiCall

/**
 * The timeline, cached by local day (ADR 0002). [refreshDay] reads every page of
 * `GET /timeline?day=` for Home; History reads one page at a time ([fetchHistoryPage]) into the
 * same rows, so a card is the same whichever screen fetched it, and live updates patch both. A
 * camera's recent clips read the device's newest cards, every day together ([refreshRecentClips]),
 * each filed under its own local day.
 * Media paths are cached as the server sent them and made absolute on the current server when read.
 */
class TimelineRepositoryImpl @Inject constructor(
    private val api: PetkitApi,
    private val dao: TimelineDao,
    private val clock: Clock,
    private val serverAddress: ServerAddressProvider,
) : TimelineRepository {
    override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> =
        dao.observeDay(day.toString()).map { rows -> rows.toDomain() }

    override suspend fun refreshDay(day: LocalDate): Outcome<Unit> {
        val key = day.toString()
        val items = mutableListOf<TimelineItemDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = when (
                val result = apiCall {
                    api.getTimeline(day = key, limit = DAY_PAGE_SIZE, cursor = cursor)
                }
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
            items += page.items
            cursor = page.nextCursor
            if (cursor == null) {
                dao.replaceDay(key, items.map { it.toRow(key) })
                return Outcome.Success(Unit)
            }
        }
        // A day of more than MAX_PAGES * PAGE_SIZE cards: a cursor that never ends is a server bug.
        return Outcome.Failure(DomainError.UnexpectedResponse("More than $MAX_PAGES pages of timeline for $key"))
    }

    override fun observeHistory(query: HistoryQuery): Flow<List<TimelineItem>> =
        dao.observeHistory(query.toFilter()).map { rows -> rows.toDomain() }

    override suspend fun fetchHistoryPage(query: HistoryQuery, cursor: HistoryCursor?): Outcome<HistoryPage> {
        val day = query.day.toString()
        val result = apiCall {
            api.getTimeline(
                device = query.deviceId,
                day = day,
                kind = query.category?.key,
                pet = query.petId,
                limit = HISTORY_PAGE_SIZE,
                cursor = cursor?.token,
            )
        }
        return result.map { page ->
            val rows = page.items.map { it.toRow(day) }
            dao.writePage(
                filter = query.toFilter(),
                items = rows,
                newerThan = cursor?.lastId?.let { TimelineKey(it, cursor.lastTs) },
                lastPage = page.nextCursor == null,
            )
            val last = page.items.lastOrNull()
            HistoryPage(
                next = page.nextCursor?.let { token ->
                    // After an empty page (which the server should not send), go on from where the page before ended.
                    if (last !=
                        null
                    ) {
                        HistoryCursor(token, last.ts, last.id)
                    } else {
                        HistoryCursor(token, cursor?.lastTs, cursor?.lastId)
                    }
                },
            )
        }
    }

    override fun observeRecentClips(deviceId: Long, limit: Int): Flow<List<TimelineItem>> =
        dao.observeRecentClips(deviceId, limit).map { rows -> rows.toDomain() }

    override suspend fun refreshRecentClips(deviceId: Long, count: Int): Outcome<Unit> {
        val items = mutableListOf<TimelineItemDto>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = when (
                val result = apiCall { api.getTimeline(device = deviceId, limit = CLIP_PAGE_SIZE, cursor = cursor) }
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
            items += page.items
            cursor = page.nextCursor
            pages++
        } while (cursor != null && pages < MAX_CLIP_PAGES && items.count { it.media?.hasClip == true } < count)
        // Read for no day: each card is filed under its own, as a live update files it.
        val today = LocalDate.now(clock)
        val rows = items.map { it.toRow(it.localDay(clock.zone, today).toString()) }
        dao.writeDeviceNewest(deviceId, rows, complete = cursor == null)
        return Outcome.Success(Unit)
    }

    override suspend fun getEvent(id: Long): Outcome<EventDetail> =
        apiCall { api.getTimelineEvent(id) }.map { it.event.toDomain(serverAddress.apiBaseUrl()) }

    private fun List<TimelineItemRow>.toDomain(): List<TimelineItem> {
        val base = serverAddress.apiBaseUrl()
        return map { it.toDomain(base) }
    }

    private companion object {
        /** The most `GET /timeline` allows: Home reads a whole day. */
        const val DAY_PAGE_SIZE = 100

        /** History's pages: a screen and a half of cards. */
        const val HISTORY_PAGE_SIZE = 25
        const val MAX_PAGES = 20

        /** A camera's recent clips: its newest cards, a few pages at most. */
        const val CLIP_PAGE_SIZE = 30
        const val MAX_CLIP_PAGES = 4
    }
}

internal fun HistoryQuery.toFilter() = TimelineFilter(
    day = day.toString(),
    deviceId = deviceId,
    petId = petId,
    filterKey = category?.key,
)

internal fun TimelineItemDto.toRow(day: String) = TimelineItemRow(
    id = id,
    day = day,
    kind = kind,
    filtersJson = encodeStrings(filters),
    deviceId = deviceId,
    deviceType = deviceType,
    ts = ts,
    displayTs = displayTs,
    eventType = eventType,
    eventKind = eventKind,
    label = label,
    petId = pet?.id,
    petName = pet?.name,
    durationSec = durationSec,
    weightG = weightG,
    factsJson = encodeStrings(facts),
    mediaJson = media?.let { PetkitJson.encodeToString(MediaDto.serializer(), it) },
    hasClip = media?.hasClip == true,
    stepsJson = PetkitJson.encodeToString(stepList, steps),
)

private val stepList = ListSerializer(TimelineStepDto.serializer())

/** The episode has a clip to play: its short clip, its whole recording or its timelapse. */
internal val MediaDto.hasClip: Boolean get() = highlight != null || playback != null || timelapse != null

/**
 * The local day a card is filed under when it was not read for a day: the day of its time in
 * [zone] (where the server cuts days too, the phone being in the same one), [today] without one.
 */
internal fun TimelineItemDto.localDay(zone: ZoneId, today: LocalDate): LocalDate =
    (ts ?: displayTs)?.let(::epochSecondsToInstant)?.atZone(zone)?.toLocalDate() ?: today

internal fun TimelineItemRow.toDomain(apiBase: HttpUrl?) = TimelineItem(
    id = id,
    kind = kind,
    categories = decodeStrings(filtersJson).mapNotNull(TimelineCategory::fromKey).toSet(),
    deviceId = deviceId,
    deviceType = deviceType,
    ts = ts?.let(::epochSecondsToInstant),
    displayTs = displayTs?.let(::epochSecondsToInstant),
    eventType = eventType,
    eventKind = eventKind,
    label = label,
    pet = petId?.let { PetRef(it, petName) },
    durationSec = durationSec,
    weightG = weightG,
    facts = decodeStrings(factsJson),
    media = mediaJson?.let(::decodeMedia)?.toDomain(apiBase),
    steps = decodeSteps(stepsJson).map { it.toDomain() },
)

private fun TimelineStepDto.toDomain() = TimelineStep(id, ts?.let(::epochSecondsToInstant), label, detail)

/** Steps cached by an older build, or unreadable, are none rather than a crash. */
private fun decodeSteps(json: String): List<TimelineStepDto> = try {
    PetkitJson.decodeFromString(stepList, json)
} catch (_: SerializationException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

private fun decodeMedia(json: String): MediaDto? = try {
    PetkitJson.decodeFromString(MediaDto.serializer(), json)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

internal fun EventDetailDto.toDomain(apiBase: HttpUrl?) = EventDetail(
    id = id,
    deviceId = deviceId,
    deviceType = deviceType,
    ts = ts?.let(::epochSecondsToInstant),
    eventType = eventType,
    eventKind = eventKind,
    label = label,
    pet = pet?.let { PetRef(it.id, it.name) },
    fields = fields.map { EventField(it.key, it.label, it.text) },
    media = media?.toDomain(apiBase),
)

/**
 * The files of [MediaDto] in the order a viewer shows them, each with its thumbnail. Null when
 * there is nothing to show; a file outside the API (or with no server configured) is left out,
 * since it would be fetched without the bearer header.
 */
internal fun MediaDto.toDomain(apiBase: HttpUrl?): TimelineMedia? {
    val slots = buildList {
        snapshot?.let { add(MediaKind.Snapshot to it) }
        waste.forEach { add(MediaKind.Waste to it) }
        health.forEach { add(MediaKind.Health to it) }
        highlight?.let { add(MediaKind.Highlight to it) }
        playback?.let { add(MediaKind.Playback to it) }
        timelapse?.let { add(MediaKind.Timelapse to it) }
    }
    val files = slots.mapNotNull { (kind, path) ->
        val url = apiUrl(apiBase, path) ?: return@mapNotNull null
        MediaFile(kind, url, thumbnailUrl = thumbnailPath(path)?.let { apiUrl(apiBase, it) })
    }
    return TimelineMedia(files, videoPending).takeIf { files.isNotEmpty() || videoPending }
}

private const val MEDIA_PREFIX = "/app/v1/media/"
private const val THUMB_PREFIX = "/app/v1/media/thumb/"

/** `GET /media/thumb/{path}` stands for any photo or video of the media tree at `/media/{path}`. */
internal fun thumbnailPath(mediaPath: String): String? = mediaPath
    .takeIf { it.startsWith(MEDIA_PREFIX) && !it.startsWith(THUMB_PREFIX) }
    ?.let { THUMB_PREFIX + it.removePrefix(MEDIA_PREFIX) }
