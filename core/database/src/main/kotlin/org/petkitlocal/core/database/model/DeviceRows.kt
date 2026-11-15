package org.petkitlocal.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Rows are named *Row so they are never confused with a device's entities (EntityRow is one of
 * those). They hold what the server sent, as close to the API as a table allows: enum values stay
 * strings and JSON values stay JSON text, so a value the server adds later is kept, not lost; the
 * data layer maps them to domain models.
 */

/**
 * A `DeviceSummary`, plus the serial of its `DeviceDetail` once read. [accessoriesJson] is its
 * `accessories`, as the JSON array the server sent (`[{"id":4001,"type":"k3","kind":"purifier"}]`).
 */
@Entity(tableName = "devices")
data class DeviceRow(
    @PrimaryKey val id: Long,
    /** Its place in the server's list. */
    val position: Int,
    val type: String,
    val model: String,
    val kind: String,
    val online: Boolean,
    val transport: String,
    /** Epoch seconds. */
    @ColumnInfo(name = "last_seen") val lastSeen: Double?,
    val firmware: String?,
    @ColumnInfo(name = "is_camera") val isCamera: Boolean,
    val serial: String?,
    /** `wifi` or `ble`. */
    val connection: String = "wifi",
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "accessories_json") val accessoriesJson: String = "[]",
)

/** One `Entity` of a device. */
@Entity(
    tableName = "device_entities",
    primaryKeys = ["device_id", "key"],
    foreignKeys = [
        ForeignKey(
            entity = DeviceRow::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EntityRow(
    @ColumnInfo(name = "device_id") val deviceId: Long,
    val key: String,
    val position: Int,
    val name: String,
    val component: String,
    /** The raw value, as JSON text (`null`, `true`, `42`, `"idle"`). */
    @ColumnInfo(name = "value_json") val valueJson: String,
    val display: String?,
    val unit: String?,
    @ColumnInfo(name = "device_class") val deviceClass: String?,
    val category: String?,
    /** A JSON array of the option labels, or null. */
    @ColumnInfo(name = "options_json") val optionsJson: String?,
    val settable: Boolean,
    val min: Double?,
    val max: Double?,
    val step: Double?,
)

/** One `Action` of a device. */
@Entity(
    tableName = "device_actions",
    primaryKeys = ["device_id", "key"],
    foreignKeys = [
        ForeignKey(
            entity = DeviceRow::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ActionRow(
    @ColumnInfo(name = "device_id") val deviceId: Long,
    val key: String,
    val position: Int,
    val name: String,
    val destructive: Boolean,
)

/** A device with its entities and actions, in no particular order: sort them by `position`. */
data class DeviceWithDetails(
    @Embedded val device: DeviceRow,
    @Relation(parentColumn = "id", entityColumn = "device_id")
    val entities: List<EntityRow>,
    @Relation(parentColumn = "id", entityColumn = "device_id")
    val actions: List<ActionRow>,
)
