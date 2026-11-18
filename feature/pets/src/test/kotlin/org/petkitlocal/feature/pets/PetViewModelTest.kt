package org.petkitlocal.feature.pets

import androidx.lifecycle.SavedStateHandle
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
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.pets.ObservePetDayVisitsUseCase
import org.petkitlocal.core.domain.pets.ObservePetUseCase
import org.petkitlocal.core.domain.pets.PetRules
import org.petkitlocal.core.domain.pets.RefreshPetUseCase
import org.petkitlocal.core.domain.pets.RefreshPetWeightsUseCase
import org.petkitlocal.core.domain.pets.WeightRange
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PetViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val today = Household.today
    private val devices = FakeDeviceRepository(Household.devices)
    private val pets = FakePetRepository(PetFixtures.pets).apply {
        activity.value = mapOf(1L to PetFixtures.activity)
        weights.value = mapOf(1L to PetFixtures.weights)
    }
    private val timeline = FakeTimelineRepository()
    private val live = FakeLiveUpdatesRepository()
    private val saved = SavedStateHandle(mapOf(PET_ID_KEY to 1L))

    private fun viewModel() = PetViewModel(
        saved,
        ObservePetUseCase(pets, devices, Household.clock),
        ObservePetDayVisitsUseCase(timeline),
        ObserveLiveUpdatesUseCase(live),
        RefreshPetUseCase(pets),
        RefreshPetWeightsUseCase(pets, Household.clock),
        TodayUseCase(Household.clock),
        Household.clock,
    )

    private fun TestScope.collect(viewModel: PetViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `shows the cache at once, then reads the pet and its 30 days of weights`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>().also { pets.refreshPetGate = it }
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Mochi", state.page?.pet?.name)
        assertEquals(3, state.page?.visitsToday)
        assertEquals("Pura Max", state.page?.lastVisitDevice?.modelName)
        assertEquals(27, state.page?.trend?.points?.size)
        assertTrue(state.loading)
        assertFalse(state.skeleton)
        assertEquals(listOf(1L), pets.refreshedPets)
        // The list too, for the switcher's names and colours.
        assertEquals(1, pets.refreshCount)
        assertEquals(listOf(1L to PetRules.since(WeightRange.Month, today, Household.zone)), pets.weightRequests)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.loading)
        assertFalse(viewModel.uiState.value.weightsLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `six months reads the weights of the longer range, and only them`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onRange(WeightRange.HalfYear)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(WeightRange.HalfYear, state.range)
        assertEquals(WeightRange.HalfYear, state.page?.trend?.range)
        assertEquals(PetRules.since(WeightRange.HalfYear, today, Household.zone), pets.weightRequests.last().second)
        assertEquals(listOf(1L), pets.refreshedPets)
    }

    @Test
    fun `the switcher moves to another pet in place, and remembers it`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onSelectPet(2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Tigrou", state.page?.pet?.name)
        assertEquals(1, state.page?.colorIndex)
        // Never read: no count of today rather than someone else's.
        assertNull(state.page?.visitsToday)
        assertEquals(listOf(1L, 2L), pets.refreshedPets)
        assertEquals(2L, pets.weightRequests.last().first)
        assertEquals(2L, saved.get<Long>(PET_ID_KEY))
    }

    @Test
    fun `a new visit of the pet in the day's cards reads its statistics again`() = runTest(main.dispatcher) {
        timeline.days.value = mapOf(today to listOf(PetFixtures.visit(205, today, 9, 12, 4290)))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertEquals(1, pets.refreshedPets.size)

        // Another pet's visit changes nothing here.
        timeline.days.value = mapOf(
            today to timeline.days.value.getValue(today) + PetFixtures.visit(206, today, 9, 40, 6000, pet = 2),
        )
        advanceUntilIdle()
        assertEquals(1, pets.refreshedPets.size)

        timeline.days.value = mapOf(
            today to timeline.days.value.getValue(today) + PetFixtures.visit(207, today, 9, 50, 4300),
        )
        advanceUntilIdle()
        assertEquals(2, pets.refreshedPets.size)
    }

    @Test
    fun `nothing cached and the server unreachable is the error view, and retry reads again`() =
        runTest(main.dispatcher) {
            pets.activity.value = emptyMap()
            pets.pets.value = emptyList()
            pets.refreshPetResult = Outcome.Failure(DomainError.Network())
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.failedEmpty)
            assertTrue(viewModel.uiState.value.offline)

            pets.refreshPetResult = Outcome.Success(Unit)
            viewModel.onRetry()
            advanceUntilIdle()
            assertEquals(2, pets.refreshedPets.size)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `a pet the server no longer has says so`() = runTest(main.dispatcher) {
        pets.pets.value = emptyList()
        pets.refreshPetResult = Outcome.Failure(DomainError.NotFound("pet_not_found", "no such pet"))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.gone)
        assertFalse(viewModel.uiState.value.failedEmpty)
    }

    @Test
    fun `the cached page stays with the offline banner when the server or the socket fails`() =
        runTest(main.dispatcher) {
            pets.refreshPetResult = Outcome.Failure(DomainError.Network())
            pets.weightsResult = Outcome.Failure(DomainError.Network())
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Mochi", state.page?.pet?.name)
            assertTrue(state.offline)
            assertTrue(state.weightsError is DomainError.Network)

            pets.refreshPetResult = Outcome.Success(Unit)
            pets.weightsResult = Outcome.Success(Unit)
            viewModel.onRetryWeights()
            viewModel.onRefresh()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.weightsError)
            assertFalse(viewModel.uiState.value.offline)

            live.current.value = LiveUpdatesStatus.Waiting(DomainError.Network(), kotlin.time.Duration.ZERO)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.offline)
        }

    @Test
    fun `pull to refresh reads the pet again and says so while it is in flight`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>().also { pets.refreshPetGate = it }

        viewModel.onRefresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.refreshing)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(2, pets.refreshedPets.size)
        assertEquals(2, pets.weightRequests.size)
    }
}
