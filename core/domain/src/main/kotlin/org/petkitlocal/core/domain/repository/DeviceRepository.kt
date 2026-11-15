package org.petkitlocal.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device

/**
 * The devices, from the local cache (ADR 0002): the flows never touch the network and emit the
 * last known data whether or not the server can be reached; [refresh] fetches from the server and
 * writes the cache, which the flows then emit.
 */
interface DeviceRepository {
    /** Every device, in the server's order. */
    fun observeDevices(): Flow<List<Device>>

    /** One device, or null when it is not (or no longer) known. */
    fun observeDevice(id: Long): Flow<Device?>

    /**
     * Fetches the device list and each device's detail, and replaces the cache with them. A
     * device the server no longer lists is removed. Fails, leaving the cache as it was, when the
     * list cannot be read; a detail that fails keeps that device's last known entities.
     */
    suspend fun refresh(): Outcome<Unit>
}
