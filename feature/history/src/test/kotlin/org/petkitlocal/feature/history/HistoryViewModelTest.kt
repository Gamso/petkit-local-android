package org.petkitlocal.feature.history

import androidx.lifecycle.SavedStateHandle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.history.FetchHistoryPageUseCase
import org.petkitlocal.core.domain.history.GetTimelineEventUseCase
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.history.ObserveHistoryUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val today = Household.today
    private val devices = FakeDeviceRepository(Household.devices)
    private val pets = FakePetRepository(Household.pets)
    private val timeline = FakeTimelineRepository().apply {
        days.value = mapOf(today to HistoryFixtures.items)
        events[12] = HistoryFixtures.visitDetail
    }
    private val live = FakeLiveUpdatesRepository()

    private fun viewModel(deviceId: Long? = null, petId: Long? = null, day: String? = null) = HistoryViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to deviceId, PET_ID_KEY to petId, DAY_KEY to day)),
        ObserveHistoryUseCase(timeline, devices, pets),
        ObserveLiveUpdatesUseCase(live),
        FetchHistoryPageUseCase(timeline),
        GetTimelineEventUseCase(timeline),
        TodayUseCase(Household.clock),
        Household.clock,
        ClipPlayerFactory { error("no player in these tests") },
    )

    private fun TestScope.collect(viewModel: HistoryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun cursor(token: String) = HistoryCursor(token, lastTs = 1.0, lastId = 1)

    @Test
    fun `opens on today for every device, shows the cache at once and asks for the first page`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { timeline.pageGate = it }
            val viewModel = viewModel()
            collect(viewModel)
            runCurrent()

            with(viewModel.uiState.value) {
                assertEquals(HistoryQuery(today), query)
                assertEquals(HistoryFixtures.items.map { it.id }, entries.map { it.item.id })
                assertEquals("Purobot Max Pro 2", entries.first { it.item.id == 12L }.device?.model)
                assertEquals((9..15).map { today.withDayOfMonth(it) }, week)
                assertFalse(hasNextWeek)
                assertTrue(paging.loading)
                assertFalse(loading) // The cache is on screen: no skeleton.
            }
            assertEquals(listOf(HistoryQuery(today) to null), timeline.pageRequests)

            gate.complete(Unit)
            advanceUntilIdle()
            with(viewModel.uiState.value.paging) {
                assertTrue(firstLoaded)
                assertTrue(endReached)
                assertFalse(loading)
            }
        }

    @Test
    fun `opened from a device page, it shows and fetches that device only`() = runTest(main.dispatcher) {
        val viewModel = viewModel(deviceId = 1002)
        collect(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1002L, state.query.deviceId)
        assertEquals("Pura Max", state.device?.model)
        assertEquals(listOf(16L, 14L, 11L, 10L), state.entries.map { it.item.id })
        assertEquals(HistoryQuery(today, deviceId = 1002), timeline.pageRequests.single().first)
    }

    @Test
    fun `opened from a pet's visit, it shows that pet on that day, in the strip that holds it`() =
        runTest(main.dispatcher) {
            val day = today.minusDays(9)
            val viewModel = viewModel(petId = 2, day = day.toString())
            collect(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(HistoryQuery(day, petId = 2), state.query)
            assertEquals(today.minusDays(7), state.weekEnd)
            assertTrue(day in state.week)
            assertEquals(HistoryQuery(day, petId = 2), timeline.pageRequests.single().first)
        }

    @Test
    fun `a day that is not one, or not yet, opens on today`() = runTest(main.dispatcher) {
        assertEquals(today, viewModel(day = "yesterday").uiState.value.query.day)
        assertEquals(today, viewModel(day = today.plusDays(1).toString()).uiState.value.query.day)
    }

    @Test
    fun `pages follow the cursor as the list nears its end, one at a time, until the last`() =
        runTest(main.dispatcher) {
            timeline.pageResult = { _, cursor ->
                Outcome.Success(HistoryPage(if (cursor == null) cursor("c1") else null))
            }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.paging.canLoadMore)

            val gate = CompletableDeferred<Unit>().also { timeline.pageGate = it }
            viewModel.onLoadMore()
            viewModel.onLoadMore() // Already in flight: ignored.
            runCurrent()
            assertTrue(viewModel.uiState.value.paging.loading)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(null, cursor("c1")), timeline.pageRequests.map { it.second })
            assertTrue(viewModel.uiState.value.paging.endReached)
            viewModel.onLoadMore()
            advanceUntilIdle()
            assertEquals(2, timeline.pageRequests.size)
        }

    @Test
    fun `a chip, a day or a device starts the paging over for the new query`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onCategory(TimelineCategory.Toileting)
        advanceUntilIdle()
        assertEquals(listOf(16L, 14L, 12L, 10L), viewModel.uiState.value.entries.map { it.item.id })

        viewModel.onDevice(1003)
        advanceUntilIdle()
        viewModel.onSelectDay(today.minusDays(1))
        advanceUntilIdle()

        assertEquals(
            listOf(
                HistoryQuery(today),
                HistoryQuery(today, TimelineCategory.Toileting),
                HistoryQuery(today, TimelineCategory.Toileting, deviceId = 1003),
                HistoryQuery(today.minusDays(1), TimelineCategory.Toileting, deviceId = 1003),
            ),
            timeline.pageRequests.map { it.first },
        )
        // Nothing cached for yesterday, and the server says so: the empty state, not a skeleton.
        with(viewModel.uiState.value) {
            assertTrue(empty)
            assertFalse(loading)
        }
        // The same choice again changes nothing.
        viewModel.onDevice(1003)
        advanceUntilIdle()
        assertEquals(4, timeline.pageRequests.size)
    }

    @Test
    fun `a late page of an earlier query does not touch the paging of the current one`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>().also { timeline.pageGate = it }
        timeline.pageResult = { query, _ ->
            Outcome.Success(HistoryPage(if (query.category == null) cursor("all") else null))
        }
        val viewModel = viewModel()
        collect(viewModel)
        runCurrent()

        viewModel.onCategory(TimelineCategory.Feeding)
        gate.complete(Unit)
        advanceUntilIdle()

        with(viewModel.uiState.value.paging) {
            assertTrue(endReached)
            assertNull(next)
        }
    }

    @Test
    fun `the week strip moves a week at a time, never past today`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        viewModel.onSelectDay(today.minusDays(2))
        advanceUntilIdle()

        viewModel.onShiftWeek(-1)
        advanceUntilIdle()
        with(viewModel.uiState.value) {
            assertEquals(today.minusDays(7), weekEnd)
            assertEquals(today.minusDays(9), query.day)
            assertTrue(hasNextWeek)
        }

        viewModel.onShiftWeek(1)
        advanceUntilIdle()
        with(viewModel.uiState.value) {
            assertEquals(today, weekEnd)
            assertEquals(today.minusDays(2), query.day)
            assertFalse(hasNextWeek)
        }
        assertEquals(today.minusDays(9), timeline.pageRequests[2].first.day)
    }

    @Test
    fun `nothing cached and the server unreachable is the error view, and retry asks again`() =
        runTest(main.dispatcher) {
            timeline.days.value = emptyMap()
            timeline.pageResult = { _, _ -> Outcome.Failure(DomainError.Network()) }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertTrue(failedEmpty)
                assertTrue(offline)
                assertFalse(loading)
            }

            timeline.pageResult = { _, _ -> Outcome.Success(HistoryPage(null)) }
            viewModel.onRetry()
            advanceUntilIdle()
            assertEquals(listOf<HistoryCursor?>(null, null), timeline.pageRequests.map { it.second })
            assertTrue(viewModel.uiState.value.empty)
        }

    @Test
    fun `cached cards stay on screen with the offline banner when a page or the socket fails`() =
        runTest(main.dispatcher) {
            timeline.pageResult = { _, cursor ->
                if (cursor ==
                    null
                ) {
                    Outcome.Success(HistoryPage(cursor("c1")))
                } else {
                    Outcome.Failure(DomainError.Network())
                }
            }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()
            viewModel.onLoadMore()
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(HistoryFixtures.items.size, entries.size)
                assertTrue(offline)
                assertFalse(paging.canLoadMore)
            }

            // Retrying a failed later page asks for that page again.
            timeline.pageResult = { _, _ -> Outcome.Success(HistoryPage(null)) }
            viewModel.onRetry()
            advanceUntilIdle()
            assertEquals(cursor("c1"), timeline.pageRequests.last().second)
            assertFalse(viewModel.uiState.value.offline)

            live.current.value = LiveUpdatesStatus.Waiting(DomainError.Network(), 2.seconds)
            runCurrent()
            assertTrue(viewModel.uiState.value.offline)
        }

    @Test
    fun `pull to refresh fetches the first page again and says so while it is in flight`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.refreshing)

        val gate = CompletableDeferred<Unit>().also { timeline.pageGate = it }
        viewModel.onRefresh()
        runCurrent()
        assertTrue(viewModel.uiState.value.refreshing)
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(listOf<HistoryCursor?>(null, null), timeline.pageRequests.map { it.second })
    }

    @Test
    fun `a live card shows up in the list, and an open card replaced meanwhile stays in its sheet`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()
            viewModel.onOpenEntry(HistoryFixtures.visit)
            advanceUntilIdle()

            val arrived = Household.item(
                19,
                1002,
                Household.at(9, 45),
                TimelineCategory.Toileting,
                pet = 1,
                duration = 70,
            )
            timeline.days.value = mapOf(today to listOf(arrived) + HistoryFixtures.items.filter { it.id != 12L })
            runCurrent()

            with(viewModel.uiState.value) {
                assertEquals(19L, entries.first().item.id)
                assertEquals(12L, sheet?.entry?.item?.id)
            }
        }

    @Test
    fun `a card opens its sheet with the event's decoded fields, and its media full screen`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { timeline.eventGate = it }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            viewModel.onOpenEntry(HistoryFixtures.visit)
            runCurrent()
            with(viewModel.uiState.value.sheet!!) {
                assertTrue(loading)
                assertEquals(HistoryFixtures.visit.item.media, media)
            }
            gate.complete(Unit)
            advanceUntilIdle()
            with(viewModel.uiState.value.sheet!!) {
                assertFalse(loading)
                assertEquals(HistoryFixtures.visitDetail, detail)
            }

            viewModel.onOpenPhoto(HistoryFixtures.waste)
            runCurrent()
            assertEquals(
                MediaViewerState.Photos(listOf(HistoryFixtures.snapshot, HistoryFixtures.waste), index = 1),
                viewModel.uiState.value.viewer,
            )
            viewModel.onOpenClip(HistoryFixtures.highlight)
            runCurrent()
            assertEquals(MediaViewerState.Clip(HistoryFixtures.highlight), viewModel.uiState.value.viewer)
            viewModel.onCloseViewer()
            runCurrent()
            assertNull(viewModel.uiState.value.viewer)

            viewModel.onCloseSheet()
            runCurrent()
            assertNull(viewModel.uiState.value.sheet)
        }

    @Test
    fun `an event that cannot be read says so in the sheet, and retry reads it again`() = runTest(main.dispatcher) {
        timeline.eventError = DomainError.Network()
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onOpenEntry(HistoryFixtures.visit)
        advanceUntilIdle()
        assertEquals(DomainError.Network(), viewModel.uiState.value.sheet?.error)

        timeline.eventError = null
        viewModel.onRetryEvent()
        advanceUntilIdle()
        with(viewModel.uiState.value.sheet!!) {
            assertNull(error)
            assertEquals(HistoryFixtures.visitDetail, detail)
        }
    }
}
