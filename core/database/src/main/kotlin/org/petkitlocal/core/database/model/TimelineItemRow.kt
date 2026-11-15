package org.petkitlocal.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A `TimelineItem`, filed under the local [day] it was fetched for, with its media. Its steps are
 * not cached: the detail sheet reads the event itself.
 */
@Entity(
    tableName = "timeline_items",
    indices = [Index("day"), Index("device_id")],
)
data class TimelineItemRow(
    @PrimaryKey val id: Long,
    /** `YYYY-MM-DD`: the `day` filter it was fetched with. */
    val day: String,
    val kind: String,
    /** A JSON array of the filter keys. */
    @ColumnInfo(name = "filters_json") val filtersJson: String,
    @ColumnInfo(name = "device_id") val deviceId: Long?,
    @ColumnInfo(name = "device_type") val deviceType: String?,
    /** Epoch seconds. */
    val ts: Double?,
    @ColumnInfo(name = "display_ts") val displayTs: Double?,
    @ColumnInfo(name = "event_type") val eventType: String?,
    @ColumnInfo(name = "event_kind") val eventKind: String?,
    val label: String?,
    @ColumnInfo(name = "pet_id") val petId: Long?,
    @ColumnInfo(name = "pet_name") val petName: String?,
    @ColumnInfo(name = "duration_sec") val durationSec: Int?,
    @ColumnInfo(name = "weight_g") val weightG: Int?,
    /** A JSON array of strings. */
    @ColumnInfo(name = "facts_json") val factsJson: String,
    /** The `Media` object as the server sent it (API paths), or null when the card has none. */
    @ColumnInfo(name = "media_json") val mediaJson: String? = null,
)
