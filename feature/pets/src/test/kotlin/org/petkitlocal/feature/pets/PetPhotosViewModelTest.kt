package org.petkitlocal.feature.pets

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.pets.AddPetFaceUseCase
import org.petkitlocal.core.domain.pets.DeletePetFaceUseCase
import org.petkitlocal.core.domain.pets.FaceWarning
import org.petkitlocal.core.domain.pets.ObservePetPhotosUseCase
import org.petkitlocal.core.domain.pets.RefreshPetFacesUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.pets.photos.CropProblem
import org.petkitlocal.feature.pets.photos.PetPhotosViewModel
import org.petkitlocal.feature.pets.photos.PhotoNotice
import org.petkitlocal.feature.pets.photos.PhotoStep

@OptIn(ExperimentalCoroutinesApi::class)
class PetPhotosViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val pets = FakePetRepository(PetFixtures.petsWithFaces)
    private val devices = FakeDeviceRepository(PetFixtures.devices)
    private val jpeg = ByteArray(10)

    private fun viewModel(petId: Long = 1) = PetPhotosViewModel(
        SavedStateHandle(mapOf(PET_ID_KEY to petId)),
        ObservePetPhotosUseCase(pets, devices),
        RefreshPetFacesUseCase(pets),
        AddPetFaceUseCase(pets),
        DeletePetFaceUseCase(pets),
    )

    @Test
    fun `the photos come from the cache, with the pet's cameras, and the limit from the server`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(11L, 12L), state.faces.map { it.id })
            assertEquals(listOf(1003L, 3001L), state.content?.devices?.map { it.id })
            assertEquals(6, state.maxFaces)
            assertEquals(listOf("faces 1"), pets.writes)
        }

    @Test
    fun `Add at the limit stays on the list and says why`() = runTest(main.dispatcher) {
        pets.maxFaces = 2
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAdd()

        assertEquals(PhotoStep.List, viewModel.uiState.value.step)
        assertEquals(PhotoNotice.AtLimit(2), viewModel.uiState.value.notice)
    }

    @Test
    fun `tips, a photo, its square, sent, a clean photo goes straight back to the list`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAdd()
        assertEquals(PhotoStep.Tips(), viewModel.uiState.value.step)
        viewModel.onPicked("content://photo/1")
        assertEquals(PhotoStep.Crop("content://photo/1"), viewModel.uiState.value.step)
        viewModel.onCropped(jpeg, side = 900)
        advanceUntilIdle()

        assertEquals(PhotoStep.List, viewModel.uiState.value.step)
        assertEquals(PhotoNotice.Added, viewModel.uiState.value.notice)
        assertEquals("upload 1 10", pets.writes.last())
        assertEquals(3, viewModel.uiState.value.faces.size)
    }

    @Test
    fun `a square smaller than the server's is refused here, with its size`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAdd()
        viewModel.onPicked("content://photo/1")

        viewModel.onCropped(jpeg, side = 120)

        assertEquals(PhotoStep.Crop("content://photo/1", CropProblem.TooSmall(120)), viewModel.uiState.value.step)
        assertEquals(listOf("faces 1"), pets.writes)
    }

    @Test
    fun `warnings are shown for review, Retake deletes the photo and goes back to the tips`() =
        runTest(main.dispatcher) {
            pets.uploadWarnings = listOf(FaceWarning.Blurry, FaceWarning.TooDark)
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAdd()
            viewModel.onPicked("content://photo/1")
            viewModel.onCropped(jpeg, side = 900)
            advanceUntilIdle()

            val review = viewModel.uiState.value.step as PhotoStep.Review
            assertEquals(listOf(FaceWarning.Blurry, FaceWarning.TooDark), review.warnings)

            viewModel.onRetake()
            advanceUntilIdle()
            assertEquals(PhotoStep.Tips(), viewModel.uiState.value.step)
            assertEquals("delete face ${review.face.id}", pets.writes.last())
            assertEquals(2, viewModel.uiState.value.faces.size)
        }

    @Test
    fun `Keep anyway keeps the photo`() = runTest(main.dispatcher) {
        pets.uploadWarnings = listOf(FaceWarning.NotSquare)
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAdd()
        viewModel.onPicked("content://photo/1")
        viewModel.onCropped(jpeg, side = 900)
        advanceUntilIdle()

        viewModel.onKeep()

        assertEquals(PhotoStep.List, viewModel.uiState.value.step)
        assertEquals(3, viewModel.uiState.value.faces.size)
    }

    @Test
    fun `a failed upload stays on the crop and says why, the limit reached meanwhile goes back to the list`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { pets.writeGate = it }
            pets.uploadFailure = DomainError.Network()
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAdd()
            viewModel.onPicked("content://photo/1")
            viewModel.onCropped(jpeg, side = 900)
            advanceUntilIdle()
            assertEquals(PhotoStep.Uploading("content://photo/1"), viewModel.uiState.value.step)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                PhotoStep.Crop("content://photo/1", CropProblem.Failed(DomainError.Network())),
                viewModel.uiState.value.step,
            )

            pets.uploadFailure = DomainError.Server(409, "too_many_faces", "a pet has at most 6 photos")
            viewModel.onCropped(jpeg, side = 900)
            advanceUntilIdle()
            assertEquals(PhotoStep.List, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.notice is PhotoNotice.AtLimit)
        }

    @Test
    fun `a photo is deleted after a confirmation`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDelete(11)
        assertEquals(11L, viewModel.uiState.value.confirmingDelete)
        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals("delete face 11", pets.writes.last())
        assertEquals(listOf(12L), viewModel.uiState.value.faces.map { it.id })
    }

    @Test
    fun `back from the flow returns to the list, from a review it keeps the photo`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAdd()
        viewModel.onBackInFlow()
        assertEquals(PhotoStep.List, viewModel.uiState.value.step)

        viewModel.onAdd()
        viewModel.onCameraUnavailable()
        assertEquals(PhotoStep.Tips(noCamera = true), viewModel.uiState.value.step)
    }
}
