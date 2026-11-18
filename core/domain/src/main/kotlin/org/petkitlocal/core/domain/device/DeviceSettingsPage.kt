package org.petkitlocal.core.domain.device

import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.petkitlocal.core.domain.devicepage.ConsumableRules
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.repository.DeviceRepository

/** How a device reaches petkit-local, as its identity card says it. */
sealed interface DeviceLink {
    data object Http : DeviceLink

    data object Mqtt : DeviceLink

    /** A Bluetooth accessory, through the device named [via] (null while the cache lacks it). */
    data class Bluetooth(val via: String?) : DeviceLink
}

/**
 * Who a device is (issue #59): what one needs to compare it with petkit-local's panel or to write
 * to support. A field the server sends null is null here, and left out on screen rather than
 * shown as unknown; the API has no MAC address.
 */
data class DeviceIdentity(
    /** The commercial name: the server has no name of the user's. */
    val name: String,
    val online: Boolean,
    /** Null for a transport the server added later. */
    val link: DeviceLink?,
    /** Null for an accessory, whose firmware the server does not know. */
    val firmware: String?,
    val serial: String?,
)

/** Everything a device's settings show, from the cache, by [DeviceSettingsRules]. */
data class DeviceSettingsPage(
    val device: Device,
    val identity: DeviceIdentity,
    /** The parts its page draws, each with its resets ([ConsumableRules]). */
    val consumables: List<Hotspot>,
    /** The Pura Air whose N60 is among a litter box's [consumables], when it is. */
    val puraAir: Device?,
    val groups: List<SettingGroup>,
)

object DeviceSettingsRules {
    /**
     * @param relay the device relaying [device] when it is a Bluetooth accessory, if cached.
     * @param puraAir the Pura Air in a litter box's K3 slot, if cached.
     */
    fun page(device: Device, relay: Device? = null, puraAir: Device? = null): DeviceSettingsPage = DeviceSettingsPage(
        device = device,
        identity = identity(device, relay),
        consumables = ConsumableRules.of(device, puraAir),
        puraAir = puraAir,
        groups = DeviceSections.of(device).settings,
    )

    fun identity(device: Device, relay: Device? = null): DeviceIdentity = DeviceIdentity(
        name = device.modelName,
        online = device.online,
        link = when {
            device.isAccessory -> DeviceLink.Bluetooth(relay?.takeIf { it.id == device.parentId }?.modelName)
            device.transport == DeviceTransport.Mqtt -> DeviceLink.Mqtt
            device.transport == DeviceTransport.Http -> DeviceLink.Http
            else -> null
        },
        firmware = device.firmware?.takeIf { it.isNotBlank() },
        serial = device.serial?.takeIf { it.isNotBlank() },
    )
}

/**
 * The settings of device [deviceId][invoke], from the cache only: null while the device is not
 * (or no longer) known. It emits again whenever the device, the device relaying it, or the Pura
 * Air in its K3 slot changes in the cache.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveDeviceSettingsUseCase @Inject constructor(private val devices: DeviceRepository) {
    operator fun invoke(deviceId: Long): Flow<DeviceSettingsPage?> =
        devices.observeDevice(deviceId).flatMapLatest { device ->
            if (device == null) return@flatMapLatest flowOf(null)
            val relayId = device.parentId?.takeIf { device.isAccessory }
            val puraAirId = device.puraAirId?.takeIf { device.family == DeviceFamily.LitterBox }
            combine(observe(relayId), observe(puraAirId)) { relay, puraAir ->
                DeviceSettingsRules.page(device, relay, puraAir?.takeIf { it.family == DeviceFamily.Purifier })
            }
        }

    private fun observe(id: Long?): Flow<Device?> = id?.let(devices::observeDevice) ?: flowOf(null)
}
