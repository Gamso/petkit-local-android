package org.petkitlocal.core.domain.model

import java.time.Instant

/** One pet's visit statistics, as the server computes them (`PetStats`). Weights are grams. */
data class PetStats(
    /** The median of its last weighed visits; null until it has one. */
    val medianWeightG: Double?,
    /** Visits since the server's local midnight, when it was asked. */
    val visitsToday: Int,
    val lastVisitAt: Instant?,
    val lastVisitWeightG: Double?,
    val lastVisitDurationSec: Int?,
    val lastDeviceId: Long?,
)

/**
 * What the server last said of one pet beyond its row (`GET /pets/{id}`): its [stats] and its
 * [recentVisits] (up to 20 visits of the last seven days, newest first), read at [fetchedAt].
 */
data class PetActivity(
    val petId: Long,
    val stats: PetStats,
    val recentVisits: List<TimelineItem>,
    val fetchedAt: Instant,
)

/** One weighed visit of a pet: when it happened, and what the scale read, in grams. */
data class WeightSample(val visitId: Long, val at: Instant, val weightG: Int)
