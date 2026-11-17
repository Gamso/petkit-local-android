package org.petkitlocal.core.domain.fountain

import java.time.ZoneId
import kotlin.math.roundToInt
import org.petkitlocal.core.domain.activity.ActivityRules
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.devicepage.FountainParts
import org.petkitlocal.core.domain.devicepage.HotspotRules
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.asBoolean
import org.petkitlocal.core.domain.model.asDouble

/**
 * The rules of the fountain page. Entities are read by their stable keys (petkit-local's: the W5
 * family's `w5_*`, the CTW3's `ctw3_*`, the W7H's own), the timeline by category; which parts a
 * model has is [FountainParts]'s table.
 */
object FountainRules {
    /**
     * The fountain's mode select, offered by the bottom bar when settable: the W7H's flow mode
     * (`do_not_flow`, `continuous`, `intermittent`, `motion_activated`), then the Bluetooth
     * fountains' `normal`/`smart` (`ha/entities/ble.py`).
     */
    private val MODE_KEYS = listOf("flow_mode", "w5_mode", "ctw3_mode")

    /** The mode select whose `smart` option is the smart mode chip, when the bar does not offer it. */
    private val SMART_MODE_KEYS = listOf("w5_mode", "ctw3_mode")
    private val DND_KEYS = listOf("w5_dnd", "ctw3_dnd")
    private val LOCK_KEYS = listOf("w5_child_lock", "ctw3_child_lock", "child_lock")

    /** How long the pump ran today, in seconds (`todayPumpRunTime`). */
    private val PUMP_TODAY_KEYS = listOf("w5_pump_today", "ctw3_pump_today")

    private const val SMART = "smart"
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    /**
     * @param today the cards of the local day shown, any device; [yesterday] those of the day before.
     * @param zone the phone's time zone, which places a drink on the chart.
     * @param relay the device relaying a Bluetooth fountain, if the cache holds it.
     */
    @Suppress("LongParameterList") // The page's sources, the zone and the relay.
    fun page(
        device: Device,
        pets: List<Pet>,
        today: List<TimelineItem>,
        yesterday: List<TimelineItem>,
        zone: ZoneId,
        relay: Device? = null,
    ): FountainPage {
        val cards = today.filter { it.deviceId == device.id }
        val mine = cards.filter { it.isDrink }
        val before = yesterday.filter { it.deviceId == device.id && it.isDrink }
        val recordsDrinks = FountainParts.recordsDrinks(device)
        val drinksOfBothDays = mine + before
        return FountainPage(
            device = device,
            status = HomeRules.status(device),
            relay = relay?.takeIf { device.isAccessory && it.id == device.parentId },
            modes = modes(device),
            modeBar = modeBar(device),
            hotspots = HotspotRules.fountain(device),
            actions = FountainParts.actions(device),
            recordsDrinks = recordsDrinks,
            today = drinkStats(mine),
            yesterday = drinkStats(before),
            durationsKnown = drinksOfBothDays.isEmpty() || drinksOfBothDays.any { it.durationSec != null },
            pumpTodaySec = pumpToday(device),
            drinks = drinks(mine, zone),
            perPet = if (recordsDrinks) perPet(pets, mine, before) else emptyList(),
            pets = pets,
            activity = ActivityRules.entries(cards, pets),
        )
    }

    /**
     * The status chips: smart mode, do not disturb, child lock, each only when the model has it
     * AND reports it ON. An off feature has no chip, and neither has an unreported one: a
     * Bluetooth fountain sends its settings only when its parent asks. Smart mode is a chip only
     * when the bottom bar does not offer the mode it is an option of.
     */
    fun modes(device: Device): List<FountainMode> = listOfNotNull(
        smartMode(device)?.takeIf { modeBar(device) == null },
        switchMode(device, DND_KEYS, FountainModeKind.DoNotDisturb),
        switchMode(device, LOCK_KEYS, FountainModeKind.ChildLock),
    )

    /**
     * The bottom bar's mode selector: the fountain's mode select when the server lets it be set,
     * with its options in the server's order. Null when there is none, or it cannot be written.
     */
    fun modeBar(device: Device): FountainModeBar? {
        val entity = MODE_KEYS.firstNotNullOfOrNull { device.entity(it) }
            ?.takeIf { it.component == EntityComponent.Select && it.settable && it.options.isNotEmpty() }
            ?: return null
        return FountainModeBar(entity.key, entity.options, EntityWrites.selectedOption(entity))
    }

    private fun smartMode(device: Device): FountainMode? {
        val entity = SMART_MODE_KEYS.firstNotNullOfOrNull { device.entity(it) }
            ?.takeIf { it.component == EntityComponent.Select }
            ?: return null
        return FountainMode(entity.key, FountainModeKind.Smart).takeIf { EntityWrites.selectedOption(entity) == SMART }
    }

    private fun switchMode(device: Device, keys: List<String>, kind: FountainModeKind): FountainMode? {
        val entity = keys.firstNotNullOfOrNull { device.entity(it) }
            ?.takeIf { it.component == EntityComponent.Switch }
            ?: return null
        return FountainMode(entity.key, kind).takeIf { entity.value.asBoolean == true }
    }

    /** A day's drinks among [items] (one fountain's drink cards of one day), and their average. */
    fun drinkStats(items: List<TimelineItem>): DrinkStats {
        val durations = items.mapNotNull { it.durationSec }
        return DrinkStats(items.size, durations.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
    }

    fun drinks(items: List<TimelineItem>, zone: ZoneId): List<Drink> = items
        .mapNotNull { item ->
            val local = (item.shownAt ?: return@mapNotNull null).atZone(zone).toLocalTime()
            Drink(
                itemId = item.id,
                minuteOfDay = (local.hour * MINUTES_PER_HOUR + local.minute).coerceIn(0, MINUTES_PER_DAY - 1),
                durationSec = item.durationSec,
                petId = item.pet?.id,
            )
        }
        .sortedBy { it.minuteOfDay }

    private fun perPet(pets: List<Pet>, today: List<TimelineItem>, yesterday: List<TimelineItem>): List<PetDrinks> =
        pets.mapNotNull { pet ->
            val now = today.count { it.pet?.id == pet.id }
            val before = yesterday.count { it.pet?.id == pet.id }
            PetDrinks(pet, now, before).takeIf { now > 0 || before > 0 }
        }

    private fun pumpToday(device: Device): Int? =
        PUMP_TODAY_KEYS.firstNotNullOfOrNull { device.entity(it)?.value?.asDouble }?.roundToInt()

    private val TimelineItem.isDrink: Boolean get() = TimelineCategory.Drinking in categories
}
