package org.petkitlocal.core.domain.home

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Pet

/** Everything Home shows, derived from the cache by [HomeRules]. */
data class HomeOverview(
    val devices: List<DeviceOverview>,
    val pets: List<PetOverview>,
    /** What needs attention, most urgent first. */
    val alerts: List<DeviceAlert>,
) {
    val isEmpty: Boolean get() = devices.isEmpty() && pets.isEmpty()
}

/** One device card. */
data class DeviceOverview(
    val device: Device,
    val headline: Headline,
    val status: DeviceStatus,
    /** The day's cards of this device, oldest first, placed on the 24-hour strip. */
    val dayEvents: List<DayEvent>,
)

/** One pet chip. */
data class PetOverview(
    val pet: Pet,
    /** Its place in the household's order, which picks its colour. */
    val colorIndex: Int,
    val visitsToday: Int,
)

/** The family's big figure on a device card (SPEC.md §5.2). */
sealed interface Headline {
    /** Litter box: toilet visits today. */
    data class Visits(val count: Int) : Headline

    /**
     * Feeder: what it served today. [dispensed] is the device's own daily total
     * (`total_dispensed`, in the unit the server gives) when it reports one; [meals] counts
     * today's feeding cards.
     */
    data class Served(val dispensed: Quantity?, val meals: Int) : Headline

    /** Fountain with a water sensor: whether the water is low. */
    data class Water(val low: Boolean) : Headline

    /** Fountain without a water sensor: drinks today. */
    data class Drinks(val count: Int) : Headline

    /** Any other device: its first primary percentage, e.g. a purifier's liquid. */
    data class Level(val entityKey: String, val entityName: String, val percent: Int) : Headline

    /** Nothing to show. */
    data object None : Headline
}

data class Quantity(val value: Double, val unit: String?)

/** The band at the bottom of a device card. */
sealed interface DeviceStatus {
    data object Idle : DeviceStatus

    data class Busy(val activity: BusyActivity) : DeviceStatus

    data object Offline : DeviceStatus

    /** [message] is the device's own error text, in English. */
    data class Error(val message: String) : DeviceStatus
}

sealed interface BusyActivity {
    /** A work mode from `device_status`: [mode] is its option label, e.g. `cleaning`. */
    data class Working(val mode: String) : BusyActivity

    data object Feeding : BusyActivity

    data object PetInside : BusyActivity
}

/** One card on a device's 24-hour strip. */
data class DayEvent(
    /** Local minute of the day, 0 to 1439. */
    val minuteOfDay: Int,
    val type: DayEventType,
    val petId: Long?,
    val durationSec: Int?,
)

enum class DayEventType {
    Visit,
    Drink,
    Meal,
    Cleaning,
    PetSeen,

    /** A fault or a health alert. */
    Alert,
    Other,
}

/** Something on a device that needs attention: one banner on Home. */
data class DeviceAlert(val deviceId: Long, val deviceName: String, val kind: AlertKind)

sealed interface AlertKind {
    /** The device reports an error: [message] is its text, in English. */
    data class Error(val message: String) : AlertKind

    data object Offline : AlertKind

    /** A problem flag is raised: waste bin full, water low, filter warning... */
    data class Problem(val entityKey: String, val entityName: String) : AlertKind

    /** A part is empty, e.g. a hopper. */
    data class Empty(val entityKey: String, val entityName: String) : AlertKind

    /** A consumable counted in days is nearly spent. */
    data class RunningOut(val entityKey: String, val entityName: String, val daysLeft: Int) : AlertKind

    /** A consumable counted in percent is low: litter, filter, liquid, battery. */
    data class Low(val entityKey: String, val entityName: String, val percent: Int) : AlertKind
}
