package org.petkitlocal.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What `GET /pets/{id}` adds to a pet's row: its `PetStats` and its recent visits. Weights are
 * grams, times epoch seconds as the server sends them.
 */
@Entity(tableName = "pet_activity")
data class PetActivityRow(
    @PrimaryKey @ColumnInfo(name = "pet_id") val petId: Long,
    @ColumnInfo(name = "median_weight_g") val medianWeightG: Double?,
    @ColumnInfo(name = "visits_today") val visitsToday: Int,
    @ColumnInfo(name = "last_visit_ts") val lastVisitTs: Double?,
    @ColumnInfo(name = "last_visit_weight_g") val lastVisitWeightG: Double?,
    @ColumnInfo(name = "last_visit_duration_sec") val lastVisitDurationSec: Double?,
    @ColumnInfo(name = "last_device_id") val lastDeviceId: Long?,
    /** The `recent_visits` array (`TimelineItem`s, newest first) as the server sent it. */
    @ColumnInfo(name = "recent_visits_json") val recentVisitsJson: String,
    /** When it was read, epoch milliseconds: "today" is only today's if it was read today. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** One weighed visit of a pet, from the timeline: what its weight chart is drawn from. */
@Entity(tableName = "pet_weights", indices = [Index(value = ["pet_id", "ts"])])
data class PetWeightRow(
    /** The visit card's id. */
    @PrimaryKey @ColumnInfo(name = "visit_id") val visitId: Long,
    @ColumnInfo(name = "pet_id") val petId: Long,
    /** When the visit happened (the card's `display_ts`, else its `ts`), epoch seconds. */
    val ts: Double,
    @ColumnInfo(name = "weight_g") val weightG: Int,
)
