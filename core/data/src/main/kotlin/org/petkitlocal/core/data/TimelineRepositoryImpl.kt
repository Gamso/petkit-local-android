package org.petkitlocal.core.data

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.database.dao.TimelineDao
import org.petkitlocal.core.database.model.TimelineItemRow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.TimelineRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.dto.TimelineItemDto
import org.petkitlocal.core.network.error.apiCall

/** The timeline, cached by local day. [refreshDay] reads every page of `GET /timeline?day=`. */
class TimelineRepositoryImpl @Inject constructor(private val api: PetkitApi, private val dao: TimelineDao) :
    TimelineRepository {
    override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> =
        dao.observeDay(day.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshDay(day: LocalDate): Outcome<Unit> {
        val key = day.toString()
        val items = mutableListOf<TimelineItemDto>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val page = when (val result = apiCall { api.getTimeline(day = key, limit = PAGE_SIZE, cursor = cursor) }) {
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

    private companion object {
        /** The most `GET /timeline` allows. */
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
    }
}

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
)

internal fun TimelineItemRow.toDomain() = TimelineItem(
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
)
