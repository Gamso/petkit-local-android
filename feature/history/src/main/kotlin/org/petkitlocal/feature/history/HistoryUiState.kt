package org.petkitlocal.feature.history

import java.time.LocalDate
import java.time.ZoneId
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryDays
import org.petkitlocal.core.domain.history.HistoryEntry
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineMedia

/**
 * What History shows. [entries] are the cache's cards for [query] (ADR 0002); [paging] is where
 * the server's cursor stands for that query; the rest is the screen's own state.
 */
data class HistoryUiState(
    val today: LocalDate,
    /** The last day of the week strip; never after [today]. */
    val weekEnd: LocalDate = today,
    val query: HistoryQuery = HistoryQuery(today),
    val entries: List<HistoryEntry> = emptyList(),
    /** The cache has been read for [query]. */
    val cacheLoaded: Boolean = false,
    val devices: List<Device> = emptyList(),
    val pets: List<Pet> = emptyList(),
    val paging: HistoryPaging = HistoryPaging(),
    /** The live socket cannot reach the server: what is shown is the last known data. */
    val liveUnreachable: Boolean = false,
    /** The phone's time zone, which times are shown in. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** The card whose detail sheet is open. */
    val sheet: EventSheetState? = null,
    /** The full-screen photo viewer or clip player, over the sheet. */
    val viewer: MediaViewerState? = null,
) {
    val week: List<LocalDate> get() = HistoryDays.week(weekEnd)

    /** A later week exists: the strip does not end today. */
    val hasNextWeek: Boolean get() = weekEnd < today

    /** The device History was narrowed to, when the cache knows it. */
    val device: Device? get() = query.deviceId?.let { id -> devices.firstOrNull { it.id == id } }

    val pet: Pet? get() = query.petId?.let { id -> pets.firstOrNull { it.id == id } }

    /** Nothing to show yet, and something is on its way: the skeleton. */
    val loading: Boolean get() = entries.isEmpty() && (!cacheLoaded || (!paging.firstLoaded && paging.error == null))

    /** Nothing cached, and the first page failed: the error view with its retry. */
    val failedEmpty: Boolean get() = entries.isEmpty() && cacheLoaded && !paging.firstLoaded && paging.error != null

    /** The day has no card under these filters, as the server says. */
    val empty: Boolean get() = entries.isEmpty() && cacheLoaded && paging.firstLoaded && !paging.loading

    /** The user pulled to refresh, and the first page is on its way. */
    val refreshing: Boolean get() = paging.refreshing && paging.loading

    val offline: Boolean get() = liveUnreachable || paging.error is DomainError.Network

    companion object {
        /** The filter chips, in the server's order; null is "all". */
        val CATEGORIES: List<TimelineCategory?> = listOf(null) + TimelineCategory.entries
    }
}

/** Where `GET /timeline`'s cursor stands for the current query. */
data class HistoryPaging(
    /** The cursor of the next page, once a page said there is one. */
    val next: HistoryCursor? = null,
    /** The first page of the current query has been fetched. */
    val firstLoaded: Boolean = false,
    /** The last page has been fetched: the cached list is the whole day. */
    val endReached: Boolean = false,
    val loading: Boolean = false,
    /** The page in flight was asked for by pulling the list down. */
    val refreshing: Boolean = false,
    /** Why the last page failed; cleared by a retry. */
    val error: DomainError? = null,
) {
    /** Another page may be asked for now. */
    val canLoadMore: Boolean get() = firstLoaded && !endReached && !loading && error == null
}

/**
 * The detail sheet of one card: the card as the cache has it (kept if it disappears while open),
 * and its event's decoded fields, fetched when it opens.
 */
data class EventSheetState(
    val entry: HistoryEntry,
    val detail: EventDetail? = null,
    val loading: Boolean = true,
    val error: DomainError? = null,
) {
    /** The event's own media when read, else the card's. */
    val media: TimelineMedia? get() = detail?.media ?: entry.item.media
}

/** A full-screen view of an event's media. */
sealed interface MediaViewerState {
    /** The event's photos, paged, starting at [index]. */
    data class Photos(val photos: List<MediaFile>, val index: Int) : MediaViewerState

    data class Clip(val clip: MediaFile) : MediaViewerState
}
