package org.petkitlocal.core.domain.home

import java.time.ZoneId
import kotlin.math.roundToInt
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.asBoolean
import org.petkitlocal.core.domain.model.asDouble

/**
 * The business rules of Home: which figure a card shows, what its status band says, and (with
 * [HomeAlerts]) what needs a banner. Entities are read by their stable keys (and, where a rule
 * covers a whole class of them, by unit or device class), never by model, so a model the server
 * adds later is covered too.
 */
object HomeRules {
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val IDLE_MODE = -1.0

    /** Problem flags that say the water is low, by key suffix. */
    private val waterLowKeys = listOf("water_missing", "water_lack")

    /**
     * A Pura Air linked to a listed litter box has no card: it is that box's deodorizer, and its
     * N60 level is on the box's page (the owner's call, so the box's card opens the box). A
     * Bluetooth fountain keeps a card of its own, which names its relay. A Pura Air whose parent is
     * not listed has nowhere else to be, so it keeps a card.
     *
     * @param dayItems the cards of the local day being shown (today), any device.
     * @param zone the phone's time zone, which places each card on the 24-hour strip.
     */
    fun overview(devices: List<Device>, pets: List<Pet>, dayItems: List<TimelineItem>, zone: ZoneId): HomeOverview {
        val byDevice = dayItems.groupBy { it.deviceId }
        val byId = devices.associateBy { it.id }
        return HomeOverview(
            devices = devices.filterNot { it.isShownByItsParent(byId) }.map { device ->
                val items = byDevice[device.id].orEmpty()
                DeviceOverview(
                    device = device,
                    headline = headline(device, items),
                    status = status(device),
                    dayEvents = dayEvents(items, zone),
                    relay = device.parentId?.takeIf { device.isAccessory }?.let(byId::get)?.model,
                )
            },
            pets = pets.mapIndexed { index, pet ->
                PetOverview(pet = pet, colorIndex = index, visitsToday = visitsOf(pet.id, dayItems))
            },
            alerts = HomeAlerts.alerts(devices),
        )
    }

    /** A Pura Air whose litter box is listed: the box's page shows it, Home does not. */
    private fun Device.isShownByItsParent(byId: Map<Long, Device>): Boolean =
        isAccessory && family == DeviceFamily.Purifier && parentId?.let(byId::get) != null

    /** Toilet visits by [petId] among [dayItems]: the summaries of the local day. */
    fun visitsOf(petId: Long, dayItems: List<TimelineItem>): Int =
        dayItems.count { TimelineCategory.Toileting in it.categories && it.pet?.id == petId }

    fun headline(device: Device, dayItems: List<TimelineItem>): Headline = when (device.family) {
        DeviceFamily.LitterBox -> Headline.Visits(dayItems.count { TimelineCategory.Toileting in it.categories })

        DeviceFamily.Feeder -> Headline.Served(
            dispensed = device.entity("total_dispensed")?.let { entity ->
                entity.value.asDouble?.let { Quantity(it, entity.unit) }
            },
            meals = dayItems.count { TimelineCategory.Feeding in it.categories },
        )

        DeviceFamily.Fountain -> {
            val water = device.entities.firstOrNull { entity -> waterLowKeys.any { entity.key.endsWith(it) } }
            val low = water?.value?.asBoolean
            if (low != null) {
                Headline.Water(low)
            } else {
                Headline.Drinks(dayItems.count { TimelineCategory.Drinking in it.categories })
            }
        }

        DeviceFamily.Purifier, DeviceFamily.Other -> device.entities.firstNotNullOfOrNull { entity ->
            entity.primaryPercent()?.let { Headline.Level(entity.key, entity.name, it.roundToInt()) }
        } ?: Headline.None
    }

    fun status(device: Device): DeviceStatus {
        if (!device.online) return DeviceStatus.Offline
        device.errorText()?.let { return DeviceStatus.Error(it) }
        val mode = device.entity("device_status")
        val modeValue = mode?.value?.asDouble
        return when {
            mode?.display != null && modeValue != null && modeValue != IDLE_MODE ->
                DeviceStatus.Busy(BusyActivity.Working(mode.display))

            device.isOn("feeding") -> DeviceStatus.Busy(BusyActivity.Feeding)

            device.isOn("pet_occupied") -> DeviceStatus.Busy(BusyActivity.PetInside)

            else -> DeviceStatus.Idle
        }
    }

    /** The day's cards on the strip, oldest first; a card with no time is left off. */
    fun dayEvents(items: List<TimelineItem>, zone: ZoneId): List<DayEvent> = items
        .mapNotNull { item ->
            val at = item.shownAt ?: return@mapNotNull null
            val local = at.atZone(zone).toLocalTime()
            DayEvent(
                minuteOfDay = (local.hour * MINUTES_PER_HOUR + local.minute).coerceIn(0, MINUTES_PER_DAY - 1),
                type = eventType(item.categories),
                petId = item.pet?.id,
                durationSec = item.durationSec,
            )
        }
        .sortedBy { it.minuteOfDay }

    private fun eventType(categories: Set<TimelineCategory>): DayEventType = when {
        TimelineCategory.Fault in categories || TimelineCategory.HealthAlert in categories -> DayEventType.Alert
        TimelineCategory.Toileting in categories -> DayEventType.Visit
        TimelineCategory.Drinking in categories -> DayEventType.Drink
        TimelineCategory.Feeding in categories -> DayEventType.Meal
        TimelineCategory.Cleaning in categories -> DayEventType.Cleaning
        TimelineCategory.Pet in categories -> DayEventType.PetSeen
        else -> DayEventType.Other
    }

    /** A primary sensor's value, when it is a percentage. */
    private fun Entity.primaryPercent(): Double? =
        value.asDouble?.takeIf { isPrimary && component == EntityComponent.Sensor && unit == "%" }

    private fun Device.isOn(key: String): Boolean = entity(key)?.value?.asBoolean == true
}
