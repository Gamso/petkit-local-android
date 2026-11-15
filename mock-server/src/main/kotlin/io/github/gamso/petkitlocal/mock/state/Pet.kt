package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.PetDto

/** A pet as petkit-local's `PetRegistry` holds it. */
class Pet(
    val id: Int,
    val name: String,
    val deviceIds: List<Int>,
    /** The weight the owner typed. */
    val enteredWeightG: Double,
    /** The weight learned from visits, once trusted; null while it is not. */
    var learnedWeightG: Double?,
    val hasPhoto: Boolean,
) {
    val weightLearned get() = learnedWeightG != null
    val weightG get() = learnedWeightG ?: enteredWeightG

    fun dto() =
        PetDto(
            id = id,
            name = name,
            deviceIds = deviceIds,
            enteredWeightG = enteredWeightG,
            weightG = weightG,
            weightLearned = weightLearned,
            photo = if (hasPhoto) "$API_PREFIX/pets/$id/photo" else null,
        )
}
