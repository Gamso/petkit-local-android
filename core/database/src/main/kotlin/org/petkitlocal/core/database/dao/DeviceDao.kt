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
