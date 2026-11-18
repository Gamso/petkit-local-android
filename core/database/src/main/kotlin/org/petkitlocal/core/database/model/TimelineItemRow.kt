package org.petkitlocal.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A `TimelineItem`, filed under the local [day] it was fetched for, with its media and its steps.
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
    /**
     * What a finished feed put out (`served`): [servedTotal] null when the card says nothing of it;
     * the hoppers null on a single hopper, or a hopper the report left out.
     */
    @ColumnInfo(name = "served_total") val servedTotal: Int? = null,
    @ColumnInfo(name = "served_hopper1") val servedHopper1: Int? = null,
    @ColumnInfo(name = "served_hopper2") val servedHopper2: Int? = null,
    /** A JSON array of strings. */
    @ColumnInfo(name = "facts_json") val factsJson: String,
    /** The `Media` object as the server sent it (API paths), or null when the card has none. */
    @ColumnInfo(name = "media_json") val mediaJson: String? = null,
    /** [mediaJson] holds a clip: a device's recent clips are read without decoding every card. */
    @ColumnInfo(name = "has_clip") val hasClip: Boolean = false,
    /** The `steps` array as the server sent it: a device page lists them under their card. */
    @ColumnInfo(name = "steps_json") val stepsJson: String = "[]",
)
