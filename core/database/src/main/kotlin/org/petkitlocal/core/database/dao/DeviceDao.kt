package org.petkitlocal.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.database.model.ActionRow
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.DeviceWithDetails
import org.petkitlocal.core.database.model.EntityRow

/** The fresh detail of one device: its entities and actions, replacing the stored ones. */
data class DeviceDetailRows(val entities: List<EntityRow>, val actions: List<ActionRow>)

/** One entity's new value, as a live `state` message carries it. */
data class EntityValuePatch(val key: String, val valueJson: String, val display: String?)

// One function per way the cache is written: a refresh, and each live update or write answer.
@Suppress("TooManyFunctions")
@Dao
abstract class DeviceDao {
    @Transaction
    @Query("SELECT * FROM devices ORDER BY position, id")
    abstract fun observeDevices(): Flow<List<DeviceWithDetails>>

    @Transaction
    @Query("SELECT * FROM devices WHERE id = :id")
    abstract fun observeDevice(id: Long): Flow<DeviceWithDetails?>

    @Query("SELECT * FROM devices WHERE id = :id")
    abstract suspend fun getDevice(id: Long): DeviceRow?

    /**
     * Makes the cache the server's list: [devices] are written (a device the server no longer
     * lists is removed, with its entities and actions), and each device in [details] gets its
     * entities and actions replaced. A device missing from [details] keeps its last known ones.
     */
    @Transaction
    open suspend fun replaceAll(devices: List<DeviceRow>, details: Map<Long, DeviceDetailRows>) {
        deleteDevicesNotIn(devices.map { it.id })
        upsertDevices(devices)
        details.forEach { (deviceId, detail) ->
            deleteEntities(deviceId)
            insertEntities(detail.entities)
            deleteActions(deviceId)
            insertActions(detail.actions)
        }
    }

    /**
     * A new value for one entity, as a live `state` message or a write's answer carries it; the
     * rest of the entity stays as it is. Nothing happens for an entity the cache does not hold.
     */
    @Query(
        "UPDATE device_entities SET value_json = :valueJson, display = :display " +
            "WHERE device_id = :deviceId AND key = :key",
    )
    abstract suspend fun patchEntityValue(deviceId: Long, key: String, valueJson: String, display: String?)

    /** Every value of one `state` message at once, so a screen never sees half of them. */
    @Transaction
    open suspend fun patchEntityValues(deviceId: Long, patches: List<EntityValuePatch>) {
        patches.forEach { patchEntityValue(deviceId, it.key, it.valueJson, it.display) }
    }

    /**
     * A device's summary from a live `device` message: an existing one keeps its place, serial,
     * entities and actions; a new one goes to the end of the list, without details yet.
     */
    @Transaction
    open suspend fun upsertSummary(device: DeviceRow) {
        val known = getDevice(device.id)
        val row = if (known != null) {
            device.copy(position = known.position, serial = known.serial)
        } else {
            device.copy(position = (maxPosition() ?: -1) + 1)
        }
        upsertDevices(listOf(row))
    }

    /** The detail of one device, replacing its stored entities and actions. */
    @Transaction
    open suspend fun replaceDetail(deviceId: Long, serial: String?, detail: DeviceDetailRows) {
        val known = getDevice(deviceId) ?: return
        upsertDevices(listOf(known.copy(serial = serial)))
        deleteEntities(deviceId)
        insertEntities(detail.entities)
        deleteActions(deviceId)
        insertActions(detail.actions)
    }

    /** Removes a device, with its entities and actions. */
    @Query("DELETE FROM devices WHERE id = :id")
    abstract suspend fun deleteDevice(id: Long)

    @Query("SELECT MAX(position) FROM devices")
    protected abstract suspend fun maxPosition(): Int?

    @Query("DELETE FROM devices WHERE id NOT IN (:ids)")
    protected abstract suspend fun deleteDevicesNotIn(ids: List<Long>)

    // An upsert updates a row in place; a REPLACE would delete it first and cascade to its
    // entities and actions.
    @Upsert
    protected abstract suspend fun upsertDevices(devices: List<DeviceRow>)

    @Query("DELETE FROM device_entities WHERE device_id = :deviceId")
    protected abstract suspend fun deleteEntities(deviceId: Long)

    @Insert
    protected abstract suspend fun insertEntities(entities: List<EntityRow>)

    @Query("DELETE FROM device_actions WHERE device_id = :deviceId")
    protected abstract suspend fun deleteActions(deviceId: Long)

    @Insert
    protected abstract suspend fun insertActions(actions: List<ActionRow>)
}
