package org.petkitlocal.feature.pets

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
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.pets.ObservePetsUseCase
import org.petkitlocal.core.domain.pets.RefreshPetsUseCase
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PetsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val today = Household.today
    private val pets = FakePetRepository(Household.pets)
    private val timeline = FakeTimelineRepository().apply { days.value = mapOf(today to Household.todayItems) }

    private fun viewModel() = PetsViewModel(
        ObservePetsUseCase(pets, timeline),
        RefreshPetsUseCase(pets, timeline),
        TodayUseCase(Household.clock),
    )

    private fun TestScope.collect(viewModel: PetsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `shows each pet with today's visits from the cache, and refreshes the pets and the day`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { pets.gate = it }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Mochi", "Tigrou"), state.pets?.map { it.pet.name })
            assertEquals(listOf(0, 1), state.pets?.map { it.colorIndex })
            assertEquals(listOf(2, 2), state.pets?.map { it.visitsToday })
            assertTrue(state.refreshing)
            assertEquals(listOf(today), timeline.refreshedDays)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.refreshing)
        }

    @Test
    fun `a failed refresh keeps the list and says the server cannot be reached`() = runTest(main.dispatcher) {
        pets.refreshResult = Outcome.Failure(DomainError.Network())
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals(2, viewModel.uiState.value.pets?.size)

        pets.refreshResult = Outcome.Success(Unit)
        viewModel.onRefresh()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.refreshError)
    }
}
