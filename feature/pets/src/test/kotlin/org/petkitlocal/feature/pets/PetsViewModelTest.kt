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
import org.petkitlocal.core.domain.pets.GetPetsSourceUseCase
import org.petkitlocal.core.domain.pets.ObservePetsUseCase
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting
import org.petkitlocal.core.domain.pets.RefreshPetsUseCase
import org.petkitlocal.core.domain.pets.SetPetsSourceUseCase
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
        GetPetsSourceUseCase(pets),
        SetPetsSourceUseCase(pets),
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

    @Test
    fun `whose pets the devices recognise is shown only in proxy mode`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showsPetsSource)

        pets.petsSourceSetting = PROXIED
        viewModel.onRefresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showsPetsSource)
        assertEquals(PetsSource.PetKit, viewModel.uiState.value.petsSource?.source)
    }

    @Test
    fun `a server without the setting shows none, and the list still loads`() = runTest(main.dispatcher) {
        pets.petsSourceSetting = null
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.petsSource)
        assertEquals(2, viewModel.uiState.value.pets?.size)
    }

    @Test
    fun `a choice shows as taken once the server answers, and a refusal says why`() = runTest(main.dispatcher) {
        pets.petsSourceSetting = PROXIED
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { pets.writeGate = it }
        viewModel.onPetsSource(PetsSource.Local)
        advanceUntilIdle()
        assertEquals(PetsSource.Local, viewModel.uiState.value.settingSource)
        assertEquals(PetsSource.PetKit, viewModel.uiState.value.petsSource?.source)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PetsSource.Local, viewModel.uiState.value.petsSource?.source)
        assertNull(viewModel.uiState.value.settingSource)

        pets.writeFailure = DomainError.Network()
        viewModel.onPetsSource(PetsSource.PetKit)
        advanceUntilIdle()
        assertEquals(PetsSource.Local, viewModel.uiState.value.petsSource?.source)
        assertEquals(DomainError.Network(), viewModel.uiState.value.sourceError)
    }

    private companion object {
        val PROXIED = PetsSourceSetting(PetsSource.PetKit, proxyMode = true, PetsSource.PetKit, PetsSource.entries)
    }
}
