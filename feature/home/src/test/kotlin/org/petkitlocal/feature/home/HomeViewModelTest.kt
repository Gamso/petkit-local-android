package org.petkitlocal.feature.home

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.home.Headline
import org.petkitlocal.core.domain.home.ObserveHomeUseCase
import org.petkitlocal.core.domain.home.RefreshHomeUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val devices = FakeDeviceRepository()
    private val pets = FakePetRepository()
    private val timeline = FakeTimelineRepository()

    private fun viewModel(clock: Clock = Household.clock) = HomeViewModel(
        ObserveHomeUseCase(devices, pets, timeline, clock),
        RefreshHomeUseCase(devices, pets, timeline),
        TodayUseCase(clock),
    )

    /** Keeps [HomeViewModel.uiState] collected, as the screen does, so it stays hot. */
    private fun TestScope.collect(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `shows the cache at once, then the refresh it starts on opening`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(Household.litterBox)
        timeline.days.value = mapOf(Household.today to Household.todayItems)
        val gate = CompletableDeferred<Unit>().also { devices.gate = it }

        val viewModel = viewModel()
        assertNull(viewModel.uiState.value.overview)
        collect(viewModel)
        advanceUntilIdle()

        val cached = viewModel.uiState.value
        assertTrue(cached.refreshing)
        assertEquals(Headline.Visits(3), cached.overview!!.devices.single().headline)

        // The refresh writes the cache; the screen follows the cache, not the refresh.
        devices.devices.value = listOf(Household.litterBox, Household.feeder)
        gate.complete(Unit)
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value
        assertFalse(refreshed.refreshing)
        assertNull(refreshed.refreshError)
        assertEquals(listOf(1002L, 2001L), refreshed.overview!!.devices.map { it.device.id })
        assertEquals(1, devices.refreshCount)
        assertEquals(listOf(Household.today), timeline.refreshedDays)
    }

    @Test
    fun `an unreachable server keeps the last known data and says it is offline`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(Household.offlineFountain)
        devices.refreshResult = Outcome.Failure(DomainError.Network("timeout"))

        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.offline)
        assertEquals(DeviceStatus.Offline, state.overview!!.devices.single().status)
    }

    @Test
    fun `a server error is not offline, and a successful retry clears it`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(Household.feeder)
        pets.refreshResult = Outcome.Failure(DomainError.Server(500, null, null))

        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertEquals(DomainError.Server(500, null, null), viewModel.uiState.value.refreshError)
        assertFalse(viewModel.uiState.value.offline)

        pets.refreshResult = Outcome.Success(Unit)
        viewModel.onRefresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.refreshError)
        assertEquals(2, pets.refreshCount)
    }

    @Test
    fun `a pull while a refresh is running does not start another`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>().also { devices.gate = it }
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onRefresh()
        viewModel.onRefresh()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, devices.refreshCount)
        assertFalse(viewModel.uiState.value.refreshing)
    }

    @Test
    fun `a refresh after midnight counts the new day`() = runTest(main.dispatcher) {
        var now = Household.clock.instant()
        val clock = object : Clock() {
            override fun getZone(): ZoneId = Household.zone

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = now
        }
        val tomorrow = Household.today.plusDays(1)
        devices.devices.value = listOf(Household.litterBox)
        timeline.days.value = mapOf(Household.today to Household.todayItems, tomorrow to emptyList())

        val viewModel = viewModel(clock)
        collect(viewModel)
        advanceUntilIdle()
        assertEquals(Headline.Visits(3), viewModel.uiState.value.overview!!.devices.single().headline)

        now = ZonedDateTime.of(tomorrow.atTime(0, 5), Household.zone).toInstant()
        viewModel.onRefresh()
        advanceUntilIdle()

        assertEquals(Headline.Visits(0), viewModel.uiState.value.overview!!.devices.single().headline)
        assertEquals(listOf(Household.today, LocalDate.of(2026, 11, 16)), timeline.refreshedDays)
    }
}
