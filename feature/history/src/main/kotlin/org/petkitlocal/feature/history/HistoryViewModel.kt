package org.petkitlocal.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.history.FetchHistoryPageUseCase
import org.petkitlocal.core.domain.history.GetTimelineEventUseCase
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryDays
import org.petkitlocal.core.domain.history.HistoryEntry
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.history.ObserveHistoryUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus

/**
 * History (SPEC.md §5.4). The list is the cache's cards for the current query, newest first (ADR
 * 0002): what was cached shows at once, online or not, and live updates patch it like any other
 * screen's. The server is read one cursor page at a time into that cache: the first page when the
 * query changes, the next one when the list nears its end ([onLoadMore]).
 *
 * Paging is this ViewModel's own rather than Paging 3's: a query is one local day, a few dozen
 * cards, so a Room flow of the whole filtered day is cheap and the windowing Paging 3 brings has
 * nothing to window; what is left is a cursor, a loading flag and an error, kept here.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@Suppress("TooManyFunctions") // One function per user intent (ARCHITECTURE.md §3), and the paging behind them.
@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeHistory: ObserveHistoryUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    private val fetchPage: FetchHistoryPageUseCase,
    private val getEvent: GetTimelineEventUseCase,
    today: TodayUseCase,
    clock: Clock,
    /** Builds the clip player; the screen creates and releases it. */
    val clipPlayerFactory: ClipPlayerFactory,
) : ViewModel() {
    private val local: MutableStateFlow<HistoryUiState>
    private var pageJob: Job? = null
    private var eventJob: Job? = null

    init {
        val today = today()
        // A day asked for (a pet's visit), unless it is not one or not yet: today then.
        val day = savedStateHandle.get<String>(DAY_KEY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeIf { it <= today }
            ?: today
        local = MutableStateFlow(
            HistoryUiState(
                today = today,
                weekEnd = HistoryDays.endShowing(day, today),
                query = HistoryQuery(
                    day = day,
                    deviceId = savedStateHandle.get<Long>(DEVICE_ID_KEY),
                    petId = savedStateHandle.get<Long>(PET_ID_KEY),
                ),
                zone = clock.zone,
            ),
        )
    }

    private val query = MutableStateFlow(local.value.query)

    val uiState: StateFlow<HistoryUiState> = combine(
        query.flatMapLatest { query -> observeHistory(query).map { query to it } },
        observeLiveUpdates(),
        local,
    ) { (cachedQuery, content), live, state ->
        val current = cachedQuery == state.query
        val entries = if (current) content.entries else emptyList()
        state.copy(
            entries = entries,
            cacheLoaded = current,
            devices = content.devices,
            pets = content.pets,
            liveUnreachable = live is LiveUpdatesStatus.Waiting && live.error is DomainError.Network,
            // An open card follows the cache; one that left it (replaced live) stays as it was.
            sheet = state.sheet?.let { sheet ->
                entries.firstOrNull { it.item.id == sheet.entry.item.id }?.let { sheet.copy(entry = it) } ?: sheet
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        loadFirstPage()
    }

    fun onSelectDay(day: LocalDate) = setQuery { it.copy(day = day) }

    fun onCategory(category: TimelineCategory?) = setQuery { it.copy(category = category) }

    fun onDevice(deviceId: Long?) = setQuery { it.copy(deviceId = deviceId) }

    fun onPet(petId: Long?) = setQuery { it.copy(petId = petId) }

    /** The strip's arrows: a week earlier ([weeks] -1) or later (+1), same weekday. */
    fun onShiftWeek(weeks: Long) {
        val state = local.value
        val (end, day) = HistoryDays.shift(state.query.day, state.weekEnd, weeks, state.today)
        local.update { it.copy(weekEnd = end) }
        onSelectDay(day)
    }

    /** Pull to refresh: the first page again; what is cached stays on screen meanwhile. */
    fun onRefresh() = loadFirstPage(pulled = true)

    /** The list nears its end: the next page, when there is one and nothing is in flight. */
    fun onLoadMore() {
        val paging = local.value.paging
        if (!paging.canLoadMore) return
        loadPage(paging.next)
    }

    /** After a failed page: that page again. */
    fun onRetry() {
        val paging = local.value.paging
        if (paging.loading) return
        if (paging.firstLoaded) loadPage(paging.next) else loadFirstPage()
    }

    fun onOpenEntry(entry: HistoryEntry) {
        local.update { it.copy(sheet = EventSheetState(entry)) }
        loadEvent()
    }

    fun onRetryEvent() = loadEvent()

    fun onCloseSheet() {
        eventJob?.cancel()
        local.update { it.copy(sheet = null, viewer = null) }
    }

    /** A photo of the open sheet, full screen, among the event's other photos. */
    fun onOpenPhoto(photo: MediaFile) {
        val photos = local.value.sheet?.media?.photos ?: return
        local.update { it.copy(viewer = MediaViewerState.Photos(photos, photos.indexOf(photo).coerceAtLeast(0))) }
    }

    fun onOpenClip(clip: MediaFile) = local.update { it.copy(viewer = MediaViewerState.Clip(clip)) }

    fun onCloseViewer() = local.update { it.copy(viewer = null) }

    private fun setQuery(change: (HistoryQuery) -> HistoryQuery) {
        val next = change(local.value.query)
        if (next == local.value.query) return
        local.update { it.copy(query = next) }
        query.value = next
        loadFirstPage()
    }

    private fun loadFirstPage(pulled: Boolean = false) {
        local.update { it.copy(paging = HistoryPaging(loading = true, refreshing = pulled)) }
        load(cursor = null)
    }

    private fun loadPage(cursor: HistoryCursor?) {
        local.update { it.copy(paging = it.paging.copy(loading = true, error = null)) }
        load(cursor)
    }

    private fun load(cursor: HistoryCursor?) {
        pageJob?.cancel()
        val query = local.value.query
        pageJob = viewModelScope.launch {
            val result = fetchPage(query, cursor)
            local.update { state ->
                if (state.query != query) return@update state
                val paging = when (result) {
                    is Outcome.Success -> HistoryPaging(
                        next = result.value.next,
                        firstLoaded = true,
                        endReached = result.value.next == null,
                    )

                    is Outcome.Failure -> state.paging.copy(loading = false, error = result.error)
                }
                state.copy(paging = paging)
            }
        }
    }

    private fun loadEvent() {
        val id = local.value.sheet?.entry?.item?.id ?: return
        eventJob?.cancel()
        local.update { state -> state.copy(sheet = state.sheet?.copy(loading = true, error = null)) }
        eventJob = viewModelScope.launch {
            val result = getEvent(id)
            local.update { state ->
                val sheet = state.sheet?.takeIf { it.entry.item.id == id } ?: return@update state
                state.copy(
                    sheet = when (result) {
                        is Outcome.Success -> sheet.copy(detail = result.value, loading = false)
                        is Outcome.Failure -> sheet.copy(loading = false, error = result.error)
                    },
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
