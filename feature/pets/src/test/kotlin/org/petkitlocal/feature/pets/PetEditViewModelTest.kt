package org.petkitlocal.feature.pets

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.pets.DeletePetUseCase
import org.petkitlocal.core.domain.pets.ObservePetEditorUseCase
import org.petkitlocal.core.domain.pets.PetChanges
import org.petkitlocal.core.domain.pets.SavePetUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.pets.edit.PetEditEffect
import org.petkitlocal.feature.pets.edit.PetEditViewModel
import org.petkitlocal.feature.pets.edit.WeightProblem

@OptIn(ExperimentalCoroutinesApi::class)
class PetEditViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val pets = FakePetRepository(Household.pets)
    private val devices = FakeDeviceRepository(PetFixtures.devices)
    private var locale: Locale = Locale.getDefault()

    @BeforeEach
    fun english() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @AfterEach
    fun restore() = Locale.setDefault(locale)

    private fun viewModel(petId: Long = PetEditDestination.NEW_PET) = PetEditViewModel(
        SavedStateHandle(mapOf(PET_ID_KEY to petId)),
        ObservePetEditorUseCase(pets, devices),
        SavePetUseCase(pets),
        DeletePetUseCase(pets),
    )

    @Test
    fun `an existing pet fills the form once, and only cameras that recognise faces are offered`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel(petId = 1)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Mochi" to "4.2", state.name to state.weight)
            assertEquals(setOf(1002L, 1003L, 3001L), state.selected)
            assertEquals(listOf(1003L, 3001L), state.devices.map { it.id })
        }

    @Test
    fun `a French phone shows the weight with a comma, and either separator is read`() = runTest(main.dispatcher) {
        Locale.setDefault(Locale.FRANCE)
        val viewModel = viewModel(petId = 1)
        advanceUntilIdle()
        assertEquals("4,2", viewModel.uiState.value.weight)

        viewModel.onWeight("4,35")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("update 1 ${PetChanges(weightG = 4350.0)}", pets.writes.last())
    }

    @Test
    fun `Save checks the fields where they are, and sends nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onWeight("lots")
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.nameMissing)
        assertEquals(WeightProblem.Invalid, viewModel.uiState.value.weightProblem)

        viewModel.onName("Nala")
        viewModel.onWeight("150")
        viewModel.onSave()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.nameMissing)
        assertEquals(WeightProblem.OutOfRange, viewModel.uiState.value.weightProblem)
        assertEquals(emptyList<String>(), pets.writes)
    }

    @Test
    fun `a new pet is created with its cameras, and the app is told to open it`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onName("  Nala ")
        viewModel.onWeight("3.9")
        viewModel.onToggleDevice(1003)

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(listOf("create Nala"), pets.writes)
        val created = pets.pets.value.last()
        assertEquals(
            Triple("Nala", 3900.0, listOf(1003L)),
            Triple(created.name, created.enteredWeightG, created.deviceIds),
        )
        assertEquals(PetEditEffect.Saved(created.id, created = true), viewModel.effect.first())
    }

    @Test
    fun `an edit sends only what changed, and keeps the devices the editor did not offer`() = runTest(main.dispatcher) {
        val viewModel = viewModel(petId = 1)
        advanceUntilIdle()

        viewModel.onToggleDevice(1003)
        viewModel.onSave()
        advanceUntilIdle()

        // The Pura Max (1002) tells Mochi by her weight: it was not offered, and stays.
        assertEquals("update 1 ${PetChanges(deviceIds = listOf(1002L, 3001L))}", pets.writes.single())
        assertEquals(PetEditEffect.Saved(1, created = false), viewModel.effect.first())
    }

    @Test
    fun `emptying the weight clears it`() = runTest(main.dispatcher) {
        val viewModel = viewModel(petId = 1)
        advanceUntilIdle()

        viewModel.onWeight("")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("update 1 ${PetChanges(clearWeight = true)}", pets.writes.single())
    }

    @Test
    fun `a refused save keeps the form and says why, nothing is in flight after it`() = runTest(main.dispatcher) {
        pets.writeFailure = DomainError.Validation(null, "invalid_pet", "name must be a non-empty string")
        val gate = CompletableDeferred<Unit>().also { pets.writeGate = it }
        val viewModel = viewModel(petId = 1)
        advanceUntilIdle()
        viewModel.onName("Moka")

        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.saving)
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.saving)
        assertEquals("Moka", state.name)
        assertEquals(pets.writeFailure, state.error)
        assertEquals("Mochi", pets.pets.value.first().name)
    }

    @Test
    fun `Delete asks first, then deletes and leaves`() = runTest(main.dispatcher) {
        val viewModel = viewModel(petId = 2)
        advanceUntilIdle()

        viewModel.onDelete()
        assertTrue(viewModel.uiState.value.confirmingDelete)
        assertEquals(emptyList<String>(), pets.writes)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("delete 2"), pets.writes)
        assertEquals(PetEditEffect.Deleted, viewModel.effect.first())
    }

    @Test
    fun `a pet deleted elsewhere says so`() = runTest(main.dispatcher) {
        val viewModel = viewModel(petId = 2)
        advanceUntilIdle()

        pets.pets.value = pets.pets.value.filterNot { it.id == 2L }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.gone)
        assertNull(viewModel.uiState.value.pet)
    }
}
