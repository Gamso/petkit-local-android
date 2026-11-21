package org.petkitlocal.feature.pets.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.pets.DeletePetUseCase
import org.petkitlocal.core.domain.pets.ObservePetEditorUseCase
import org.petkitlocal.core.domain.pets.PetDraft
import org.petkitlocal.core.domain.pets.PetManagementRules
import org.petkitlocal.core.domain.pets.SavePetUseCase
import org.petkitlocal.feature.pets.PET_ID_KEY
import org.petkitlocal.feature.pets.PetEditDestination

/** Why the weight typed cannot be sent. */
enum class WeightProblem {
    /** Not a number. */
    Invalid,

    /** Zero, negative, or over the server's 100 kg. */
    OutOfRange,
}

/**
 * The pet editor: the form as typed, and what the server answered. [pet] is the cache's, null
 * for a new pet; [devices] the devices to offer, those that recognise pets by their face.
 */
data class PetEditUiState(
    val isNew: Boolean,
    val pet: Pet? = null,
    /** The cache has been read: the form holds the pet's values. */
    val loaded: Boolean = false,
    val name: String = "",
    /** In kilograms, as typed. */
    val weight: String = "",
    val selected: Set<Long> = emptySet(),
    val devices: List<Device> = emptyList(),
    /** The name was found empty when saving. */
    val nameMissing: Boolean = false,
    val weightProblem: WeightProblem? = null,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    /** The delete confirmation is open. */
    val confirmingDelete: Boolean = false,
    /** Why the last save or delete failed. */
    val error: DomainError? = null,
) {
    /** An existing pet the cache does not (or no longer) have. */
    val gone: Boolean get() = !isNew && loaded && pet == null

    val busy: Boolean get() = saving || deleting
}

/** What the editor asks the screen to do once. */
sealed interface PetEditEffect {
    /** Saved: [created] for a new pet, which the app then opens. */
    data class Saved(val petId: Long, val created: Boolean) : PetEditEffect

    data object Deleted : PetEditEffect
}

/**
 * Creates or edits a pet (SPEC.md §5.5, issue #98): its name, its weight and the devices that
 * recognise it by its face. Nothing changes in the cache until the server has answered (ADR 0002).
 * A field is checked when the owner saves, and said where it is wrong; Save is never greyed out.
 */
@HiltViewModel
class PetEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEditor: ObservePetEditorUseCase,
    private val savePet: SavePetUseCase,
    private val deletePet: DeletePetUseCase,
) : ViewModel() {
    private val petId: Long? = savedStateHandle.get<Long>(PET_ID_KEY)?.takeIf { it != PetEditDestination.NEW_PET }
    private val local = MutableStateFlow(PetEditUiState(isNew = petId == null))
    private val effects = Channel<PetEditEffect>(Channel.BUFFERED)

    /** One-shot effects: the screen collects them once. */
    val effect: Flow<PetEditEffect> = effects.receiveAsFlow()

    /** The form and the cache together; the intents read it as it is now, never a step behind. */
    val uiState: StateFlow<PetEditUiState> = local.asStateFlow()

    init {
        viewModelScope.launch {
            observeEditor(petId).collect { content ->
                local.update { state ->
                    val pet = content.pet
                    // The form takes the pet's values once, from the first read of the cache. A pet
                    // deleted elsewhere while it is open: what was typed stays, the pet is gone.
                    val filled = if (!state.loaded && pet != null) {
                        state.copy(
                            name = pet.name,
                            weight = pet.enteredWeightG?.let(::formatKg).orEmpty(),
                            selected = pet.deviceIds.toSet(),
                        )
                    } else {
                        state
                    }
                    filled.copy(loaded = true, pet = pet, devices = content.recognitionDevices)
                }
            }
        }
    }

    fun onName(name: String) = local.update { it.copy(name = name, nameMissing = false, error = null) }

    fun onWeight(weight: String) = local.update { it.copy(weight = weight, weightProblem = null, error = null) }

    fun onToggleDevice(deviceId: Long) = local.update { state ->
        val selected = if (deviceId in state.selected) state.selected - deviceId else state.selected + deviceId
        state.copy(selected = selected, error = null)
    }

    fun onSave() {
        val state = local.value
        if (state.busy || state.gone) return
        val weight = parseKg(state.weight)
        val nameMissing = state.name.isBlank()
        val weightProblem = when {
            state.weight.isBlank() -> null
            weight == null -> WeightProblem.Invalid
            weight <= 0.0 || weight * GRAMS_PER_KG > PetManagementRules.MAX_WEIGHT_G -> WeightProblem.OutOfRange
            else -> null
        }
        if (nameMissing || weightProblem != null) {
            local.update { it.copy(nameMissing = nameMissing, weightProblem = weightProblem) }
            return
        }
        val draft = PetDraft(
            name = state.name.trim(),
            weightG = weight?.let { (it * GRAMS_PER_KG).roundTo(GRAM_DECIMALS) },
            deviceIds = PetManagementRules.deviceIds(state.pet, state.devices, state.selected),
        )
        local.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = savePet(state.pet, draft)) {
                is Outcome.Success -> effects.send(PetEditEffect.Saved(result.value.id, created = state.isNew))
                is Outcome.Failure -> local.update { it.copy(error = result.error) }
            }
            local.update { it.copy(saving = false) }
        }
    }

    fun onDelete() {
        if (local.value.busy || local.value.pet == null) return
        local.update { it.copy(confirmingDelete = true, error = null) }
    }

    fun onDismissDelete() = local.update { it.copy(confirmingDelete = false) }

    fun onConfirmDelete() {
        val pet = local.value.pet ?: return
        local.update { it.copy(confirmingDelete = false, deleting = true, error = null) }
        viewModelScope.launch {
            val result = deletePet(pet.id)
            val error = (result as? Outcome.Failure)?.error
            // A pet already deleted elsewhere is as good as deleted here.
            if (error == null || (error as? DomainError.NotFound)?.code == PET_NOT_FOUND) {
                effects.send(PetEditEffect.Deleted)
            } else {
                local.update { it.copy(error = error) }
            }
            local.update { it.copy(deleting = false) }
        }
    }

    private companion object {
        const val GRAMS_PER_KG = 1000.0
        const val GRAM_DECIMALS = 1
        const val KG_DECIMALS = 2
        const val PET_NOT_FOUND = "pet_not_found"

        /** Kilograms with the phone's decimal separator, without trailing zeros: `4,2`. */
        fun formatKg(grams: Double): String {
            val kg = BigDecimal.valueOf(grams / GRAMS_PER_KG).setScale(KG_DECIMALS, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
            return kg.replace('.', DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator)
        }

        /** What was typed, in kilograms: a comma or a point as the separator; null if not a number. */
        fun parseKg(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

        fun Double.roundTo(decimals: Int): Double =
            BigDecimal.valueOf(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()
    }
}
