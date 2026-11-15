package org.petkitlocal.core.data

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.petkitlocal.core.database.dao.DeviceDao
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.dto.DeviceDetailDto
import org.petkitlocal.core.network.error.apiCall

/**
 * Devices from Room; [refresh] reads `GET /devices`, then every device's detail at once (a few at
 * a time), and writes the lot in one transaction, so a screen never sees half a refresh.
 */
class DeviceRepositoryImpl @Inject constructor(private val api: PetkitApi, private val dao: DeviceDao) :
    DeviceRepository {
    override fun observeDevices(): Flow<List<Device>> = dao.observeDevices().map { rows -> rows.map { it.toDomain() } }

    override fun observeDevice(id: Long): Flow<Device?> = dao.observeDevice(id).map { it?.toDomain() }

    override suspend fun refresh(): Outcome<Unit> {
        val list = when (val result = apiCall { api.listDevices() }) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value.devices
        }
        val details = fetchDetails(list.map { it.id })
        val rows = list.mapIndexed { index, summary ->
            // Without a fresh detail, the serial stays what it was.
            val serial = details[summary.id]?.serial ?: dao.getDevice(summary.id)?.serial
            summary.toRow(position = index, serial = serial)
        }
        dao.replaceAll(rows, details.mapValues { (_, detail) -> detail.toDetailRows() })
        return Outcome.Success(Unit)
    }

    /** The details that could be read; a device whose detail failed is left out. */
    private suspend fun fetchDetails(ids: List<Long>): Map<Long, DeviceDetailDto> = coroutineScope {
        val permits = Semaphore(PARALLEL_DETAILS)
        ids.map { id ->
            async { permits.withPermit { apiCall { api.getDevice(id) } } }
        }.awaitAll()
            .mapNotNull { (it as? Outcome.Success)?.value?.device }
            .associateBy { it.id }
    }

    private companion object {
        /** How many `GET /devices/{id}` run at once: enough to be quick, few enough for a Pi. */
        const val PARALLEL_DETAILS = 4
    }
}
