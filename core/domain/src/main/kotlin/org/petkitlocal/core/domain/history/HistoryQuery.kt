package org.petkitlocal.core.domain.history

import java.time.LocalDate
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

/**
 * What History shows: the cards of one local [day] (cut at local midnight by the server), under
 * one filter chip ([category], null for all), of one device and one pet, or of all of them.
 */
data class HistoryQuery(
    val day: LocalDate,
    val category: TimelineCategory? = null,
    val deviceId: Long? = null,
    val petId: Long? = null,
) {
    /** Whether [item], filed under [day], passes the other filters, as `GET /timeline` applies them. */
    fun matches(item: TimelineItem): Boolean = (category == null || category in item.categories) &&
        (deviceId == null || item.deviceId == deviceId) &&
        (petId == null || item.pet?.id == petId)
}

/**
 * Where the next page of a [HistoryQuery] starts: the server's opaque [token], and the sort key
 * (`ts` in epoch seconds, `id`) of the last card before it, which tells the cache which range the
 * next page covers (null [lastId]: from the top). Screens only pass it back.
 */
data class HistoryCursor(val token: String, val lastTs: Double?, val lastId: Long?)

/** What fetching one page did: [next] is null once the day has no more cards. */
data class HistoryPage(val next: HistoryCursor?)
