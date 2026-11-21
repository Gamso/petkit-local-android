package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.PetDto
import io.github.gamso.petkitlocal.mock.api.PetFaceDto

/**
 * One reference face photo. The mock does not keep the bytes it was sent: a face is a generated
 * 224x224 placeholder drawn from [seed], so every GET of it answers the same bytes.
 */
class PetFace(
    val id: Int,
    val seed: Int,
    /** The devices that report holding it (`discernPic`); the control API adds to it. */
    val acceptedBy: MutableSet<Int> = mutableSetOf(),
) {
    /**
     * As the API shows it: of [pet]'s devices that recognise pets ([recognisers]), those that hold
     * it and those still to fetch it. A device that cannot recognise pets is in neither.
     */
    fun dto(
        pet: Pet,
        recognisers: Set<Int>,
    ): PetFaceDto {
        val devices = pet.deviceIds.filter { it in recognisers }
        return PetFaceDto(
            id = id,
            photo = "$API_PREFIX/pets/${pet.id}/faces/$id/photo",
            acceptedBy = devices.filter { it in acceptedBy },
            waitingFor = devices.filterNot { it in acceptedBy },
        )
    }
}

/** A pet as petkit-local's `PetRegistry` holds it. */
class Pet(
    val id: Int,
    var name: String,
    var deviceIds: List<Int>,
    /** The weight the owner typed; null when none was. */
    var enteredWeightG: Double?,
    /** The weight learned from visits, once trusted; null while it is not. */
    var learnedWeightG: Double?,
    /** Its reference face photos, oldest first. */
    val faces: MutableList<PetFace> = mutableListOf(),
) {
    val weightLearned get() = learnedWeightG != null
    val weightG get() = learnedWeightG ?: enteredWeightG

    fun dto(recognisers: Set<Int>) =
        PetDto(
            id = id,
            name = name,
            deviceIds = deviceIds,
            enteredWeightG = enteredWeightG,
            weightG = weightG,
            weightLearned = weightLearned,
            photo = if (faces.isNotEmpty()) "$API_PREFIX/pets/$id/photo" else null,
            faces = faces.map { it.dto(this, recognisers) },
        )

    companion object {
        /** How many face photos one pet may have (`PetFaceList.max_faces`). */
        const val MAX_FACES = 6
    }
}
