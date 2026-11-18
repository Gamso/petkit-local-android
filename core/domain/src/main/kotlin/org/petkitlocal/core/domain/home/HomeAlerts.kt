package org.petkitlocal.core.domain.home

import kotlin.math.roundToInt
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.asBoolean
import org.petkitlocal.core.domain.model.asDouble

/** What needs a banner on Home, read from each device's primary entities by key, unit and class. */
object HomeAlerts {
    /** A consumable counted in days raises a banner at this many days left, or fewer. */
    const val DAYS_LEFT_ALERT = 3

    /** A consumable counted in percent raises a banner at this level, or lower. */
    const val LOW_PERCENT_ALERT = 15

    /** Percent sensors that measure a consumable (litter, filter, liquid, battery). */
    private val consumablePercent = Regex("(^|_)(litter|filter|liquid|battery)(_|$)")

    /** Binary sensors whose "on" is something to fix. */
    private val alertClasses = setOf("problem", "battery")

    /**
     * Banners, most urgent first: errors, offline devices, raised problem flags, empty parts,
     * consumables running out. An offline device gets only its offline banner: what it last
     * reported is stale.
     */
    fun alerts(devices: List<Device>): List<DeviceAlert> = devices
        .flatMap { device ->
            alertKinds(device).map { DeviceAlert(device.id, device.modelName, it) }
        }
        .sortedBy { severity(it.kind) }

    private fun alertKinds(device: Device): List<AlertKind> {
        if (!device.online) return listOf(AlertKind.Offline)
        val kinds = mutableListOf<AlertKind>()
        device.errorText()?.let { kinds += AlertKind.Error(it) }
        device.entities.filter { it.isPrimary }.forEach { entity ->
            entityAlert(entity)?.let { kinds += it }
        }
        return kinds
    }

    private fun entityAlert(entity: Entity): AlertKind? {
        val number = entity.value.asDouble
        return when {
            entity.component == EntityComponent.BinarySensor &&
                entity.deviceClass in alertClasses &&
                entity.value.asBoolean == true -> AlertKind.Problem(entity.key, entity.name)

            entity.component != EntityComponent.Sensor -> null

            entity.display == "Empty" -> AlertKind.Empty(entity.key, entity.name)

            number == null -> null

            entity.unit == "days" && number <= DAYS_LEFT_ALERT ->
                AlertKind.RunningOut(entity.key, entity.name, number.roundToInt())

            entity.unit == "%" && consumablePercent.containsMatchIn(entity.key) && number <= LOW_PERCENT_ALERT ->
                AlertKind.Low(entity.key, entity.name, number.roundToInt())

            else -> null
        }
    }

    @Suppress("MagicNumber") // An ordering, not a quantity.
    private fun severity(kind: AlertKind): Int = when (kind) {
        is AlertKind.Error -> 0
        AlertKind.Offline -> 1
        is AlertKind.Problem -> 2
        is AlertKind.Empty -> 3
        is AlertKind.RunningOut -> 4
        is AlertKind.Low -> 5
    }
}

/** The `error` entity's text, when the device reports one. */
internal fun Device.errorText(): String? =
    (entity("error")?.value as? EntityValue.Text)?.value?.trim()?.takeIf { it.isNotEmpty() }
