package org.petkitlocal.feature.pets.photos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.PetFace
import org.petkitlocal.core.domain.pets.AddPetFaceUseCase
import org.petkitlocal.core.domain.pets.DeletePetFaceUseCase
import org.petkitlocal.core.domain.pets.FaceWarning
import org.petkitlocal.core.domain.pets.ObservePetPhotosUseCase
import org.petkitlocal.core.domain.pets.PetPhotosContent
import org.petkitlocal.core.domain.pets.RefreshPetFacesUseCase
import org.petkitlocal.feature.pets.PET_ID_KEY

/** Where the photo screen is in adding a photo. */
sealed interface PhotoStep {
    /** The pet's photos. */
    data object List : PhotoStep

    /** The illustrated tips, and the way to the camera or the gallery; [noCamera] when no camera app opened. */
    data class Tips(val noCamera: Boolean = false) : PhotoStep

    /**
     * The square crop of the photo at [source] (a content URI, read by the screen). [problem] is
     * why the last square could not be used, or the upload's failure.
     */
    data class Crop(val source: String, val problem: CropProblem? = null) : PhotoStep

    /** The square is on its way. */
    data class Uploading(val source: String) : PhotoStep

    /** The photo was stored with [warnings]: kept, or deleted to take another. */
    data class Review(
        val face: PetFace,
        val warnings: kotlin.collections.List<FaceWarning>,
        val retaking: Boolean = false,
    ) : PhotoStep
}

/** Why a square was not sent, or not stored. */
sealed interface CropProblem {
    /** The photo could not be read. */
    data object Unreadable : CropProblem

    /** The square is [side] pixels of the original: under what the server needs. */
    data class TooSmall(val side: Int) : CropProblem

    data class Failed(val error: DomainError) : CropProblem
}

/** A line under the list, after something happened. */
sealed interface PhotoNotice {
    /** Add was pressed at the server's limit. */
    data class AtLimit(val max: Int) : PhotoNotice

    data object Added : PhotoNotice

    data class Failed(val error: DomainError) : PhotoNotice
}

/** The photo screen: the cache's pet and its photos, the server's limit, and the add flow. */
data class PetPhotosUiState(
    val petId: Long,
    /** Null until the cache has been read, and for a pet it does not have. */
    val content: PetPhotosContent? = null,
    val cacheLoaded: Boolean = false,
    /** How many photos a pet may have, once the server said. */
    val maxFaces: Int? = null,
    val loading: Boolean = true,
    /** Why reading the photos from the server failed. */
    val loadError: DomainError? = null,
    val step: PhotoStep = PhotoStep.List,
    val notice: PhotoNotice? = null,
    /** The photo whose deletion is being confirmed. */
    val confirmingDelete: Long? = null,
    /** The photo being deleted. */
    val deleting: Long? = null,
) {
    val faces: List<PetFace> get() = content?.pet?.faces.orEmpty()

    val gone: Boolean get() = cacheLoaded && content == null
}

/**
 * A pet's reference face photos (issue #98): listed from the cache, read again from the server
 * when the screen opens; added through the tips, the camera or the gallery, and a square crop;
 * deleted after a confirmation. At the server's limit, Add stays enabled and says why on press.
 */
@HiltViewModel
class PetPhotosViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePhotos: ObservePetPhotosUseCase,
    private val refreshFaces: RefreshPetFacesUseCase,
    private val addFace: AddPetFaceUseCase,
    private val deleteFace: DeletePetFaceUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(PetPhotosUiState(petId = checkNotNull(savedStateHandle.get<Long>(PET_ID_KEY))))

    /** The screen and the cache together; the intents read it as it is now, never a step behind. */
    val uiState: StateFlow<PetPhotosUiState> = local.asStateFlow()

    init {
        viewModelScope.launch {
            observePhotos(local.value.petId).collect { content ->
                local.update { it.copy(content = content, cacheLoaded = true) }
            }
        }
        load()
    }

    fun onRetry() = load()

    private fun load() {
        local.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val result = refreshFaces(local.value.petId)
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(loading = false, maxFaces = result.value.maxFaces)
                    is Outcome.Failure -> state.copy(loading = false, loadError = result.error)
                }
            }
        }
    }

    /** Add: the tips, unless the pet is at the server's limit, which is said instead. */
    fun onAdd() {
        val state = local.value
        val max = state.maxFaces
        if (max != null && state.faces.size >= max) {
            local.update { it.copy(notice = PhotoNotice.AtLimit(max)) }
            return
        }
        local.update { it.copy(step = PhotoStep.Tips(), notice = null) }
    }

    /** Another photo instead of this one: back to the tips and their two ways. */
    fun onChooseAnother() = local.update { it.copy(step = PhotoStep.Tips()) }

    /** No camera app took the request. */
    fun onCameraUnavailable() = local.update { state ->
        if (state.step is PhotoStep.Tips) state.copy(step = PhotoStep.Tips(noCamera = true)) else state
    }

    /** A photo taken or chosen: its square crop. */
    fun onPicked(source: String) = local.update { it.copy(step = PhotoStep.Crop(source)) }

    /** The photo could not be read at all. */
    fun onUnreadable() = local.update { state ->
        val step = state.step as? PhotoStep.Crop ?: return@update state
        state.copy(step = step.copy(problem = CropProblem.Unreadable))
    }

    /**
     * The square chosen: [jpeg] its bytes, [side] its width in pixels of the original photo. One
     * smaller than the server's own crop is refused here, with the reason.
     */
    fun onCropped(jpeg: ByteArray, side: Int) {
        val step = local.value.step as? PhotoStep.Crop ?: return
        if (side < MIN_SIDE) {
            local.update { it.copy(step = step.copy(problem = CropProblem.TooSmall(side))) }
            return
        }
        local.update { it.copy(step = PhotoStep.Uploading(step.source)) }
        viewModelScope.launch {
            val result = addFace(local.value.petId, jpeg)
            local.update { state ->
                when {
                    result is Outcome.Success && result.value.warnings.isEmpty() ->
                        state.copy(step = PhotoStep.List, notice = PhotoNotice.Added)

                    result is Outcome.Success -> state.copy(
                        step = PhotoStep.Review(result.value.face, result.value.warnings),
                    )

                    else -> {
                        val error = (result as Outcome.Failure).error
                        if ((error as? DomainError.Server)?.code == TOO_MANY_FACES) {
                            val max = state.maxFaces ?: state.faces.size
                            state.copy(step = PhotoStep.List, notice = PhotoNotice.AtLimit(max))
                        } else {
                            state.copy(step = PhotoStep.Crop(step.source, CropProblem.Failed(error)))
                        }
                    }
                }
            }
        }
    }

    /** The photo stays, warnings and all. */
    fun onKeep() = local.update { it.copy(step = PhotoStep.List, notice = PhotoNotice.Added) }

    /** The photo goes, and the tips come back for another. */
    fun onRetake() {
        val step = local.value.step as? PhotoStep.Review ?: return
        if (step.retaking) return
        local.update { it.copy(step = step.copy(retaking = true)) }
        viewModelScope.launch {
            val result = deleteFace(local.value.petId, step.face.id)
            local.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(step = PhotoStep.Tips())
                    is Outcome.Failure -> state.copy(step = PhotoStep.List, notice = PhotoNotice.Failed(result.error))
                }
            }
        }
    }

    /**
     * Back, from wherever the add flow is: to the list. A photo under review is kept, as Keep
     * would; an upload on its way finishes and says how it went.
     */
    fun onBackInFlow() = local.update { state ->
        when (state.step) {
            is PhotoStep.Review -> state.copy(step = PhotoStep.List, notice = PhotoNotice.Added)
            is PhotoStep.Uploading -> state
            else -> state.copy(step = PhotoStep.List)
        }
    }

    fun onDelete(faceId: Long) {
        if (local.value.deleting != null) return
        local.update { it.copy(confirmingDelete = faceId, notice = null) }
    }

    fun onDismissDelete() = local.update { it.copy(confirmingDelete = null) }

    fun onConfirmDelete() {
        val faceId = local.value.confirmingDelete ?: return
        local.update { it.copy(confirmingDelete = null, deleting = faceId) }
        viewModelScope.launch {
            val result = deleteFace(local.value.petId, faceId)
            local.update { state ->
                state.copy(deleting = null, notice = (result as? Outcome.Failure)?.error?.let(PhotoNotice::Failed))
            }
        }
    }

    companion object {
        /** The server stores 224x224: a smaller square would be blown up. */
        const val MIN_SIDE = 224

        private const val TOO_MANY_FACES = "too_many_faces"
    }
}
