package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Pet`. Weights are grams. */
@Serializable
data class PetDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("device_ids") val deviceIds: List<Long>,
    /** The weight the owner typed. */
    @SerialName("entered_weight_g") val enteredWeightG: Double?,
    /** What a visit is compared with: the learned weight once trusted. */
    @SerialName("weight_g") val weightG: Double?,
    @SerialName("weight_learned") val weightLearned: Boolean,
    /** `/app/v1/pets/{id}/photo`, if there is one. */
    @SerialName("photo") val photo: String?,
)

/** `PetStats`. */
@Serializable
data class PetStatsDto(
    @SerialName("median_weight_g") val medianWeightG: Double?,
    @SerialName("visits_today") val visitsToday: Int,
    @SerialName("last_visit_ts") val lastVisitTs: Double?,
    @SerialName("last_visit_weight_g") val lastVisitWeightG: Double?,
    @SerialName("last_visit_duration_sec") val lastVisitDurationSec: Double?,
    @SerialName("last_device_id") val lastDeviceId: Long?,
)

/** `PetDetail`: `Pet` plus its statistics and recent visits. */
@Serializable
data class PetDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("device_ids") val deviceIds: List<Long>,
    @SerialName("entered_weight_g") val enteredWeightG: Double?,
    @SerialName("weight_g") val weightG: Double?,
    @SerialName("weight_learned") val weightLearned: Boolean,
    @SerialName("photo") val photo: String?,
    @SerialName("stats") val stats: PetStatsDto,
    /** Up to 20 visits from the last 7 days, newest first. */
    @SerialName("recent_visits") val recentVisits: List<TimelineItemDto>,
)

/** Body of `GET /pets`. */
@Serializable
data class PetListResponseDto(@SerialName("pets") val pets: List<PetDto>)

/** Body of `GET /pets/{id}`. */
@Serializable
data class PetResponseDto(@SerialName("pet") val pet: PetDetailDto)
