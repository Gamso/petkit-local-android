package org.petkitlocal.core.domain.model

/** A pet of the household. Weights are grams. */
data class Pet(
    val id: Long,
    val name: String,
    val deviceIds: List<Long>,
    /** The weight the owner typed. */
    val enteredWeightG: Double?,
    /** What a visit is compared with: the learned weight once trusted, else the entered one. */
    val weightG: Double?,
    val weightLearned: Boolean,
    /**
     * Where its photo is, as an absolute URL on the user's server (fetched with the bearer
     * header), or null when it has none.
     */
    val photoUrl: String?,
    /** Its reference face photos, oldest first: what the cameras recognise it by. */
    val faces: List<PetFace> = emptyList(),
)

/** One reference face photo of a pet, the 224x224 square the server stores. */
data class PetFace(
    /** Unique across every pet. */
    val id: Long,
    /** Where it is, as an absolute URL on the user's server (fetched with the bearer header). */
    val photoUrl: String?,
    /**
     * The pet's devices that report holding it; null when the server does not say (one older than
     * petkit-local#132), which is not the same as none.
     */
    val acceptedBy: List<Long>? = null,
    /** The pet's devices that recognise pets and do not hold it yet; null as [acceptedBy]. */
    val waitingFor: List<Long>? = null,
)
