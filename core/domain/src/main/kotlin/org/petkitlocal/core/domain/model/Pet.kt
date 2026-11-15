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
)
