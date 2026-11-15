package org.petkitlocal.core.domain.fountain

import java.time.ZoneId
import kotlin.math.roundToInt
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.devicepage.FountainParts
import org.petkitlocal.core.domain.devicepage.HotspotRules
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
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
    /** The mode select whose `smart` option is the smart mode chip (`normal` is its "off"). */
    private val SMART_MODE_KEYS = listOf("w5_mode", "ctw3_mode")
    private val DND_KEYS = listOf("w5_dnd", "ctw3_dnd")
    private val LOCK_KEYS = listOf("w5_child_lock", "ctw3_child_lock", "child_lock")

    /** How long the pump ran today, in seconds (`todayPumpRunTime`). */
    private val PUMP_TODAY_KEYS = listOf("w5_pump_today", "ctw3_pump_today")

    private const val SMART = "smart"
    private const val NORMAL = "normal"
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
        val mine = today.filter { it.deviceId == device.id && it.isDrink }
        val before = yesterday.filter { it.deviceId == device.id && it.isDrink }
        val recordsDrinks = FountainParts.recordsDrinks(device)
        return FountainPage(
            device = device,
            status = HomeRules.status(device),
            relay = relay?.takeIf { device.isAccessory && it.id == device.parentId },
            modes = modes(device),
            hotspots = HotspotRules.fountain(device),
            actions = FountainParts.actions(device),
            recordsDrinks = recordsDrinks,
            today = drinkStats(mine),
            yesterday = drinkStats(before),
            pumpTodaySec = pumpToday(device),
            drinks = drinks(mine, zone),
            perPet = if (recordsDrinks) perPet(pets, mine, before) else emptyList(),
            pets = pets,
        )
    }

    /**
     * The chips: smart mode, do not disturb, child lock, each only when the model has it AND
     * reports where it stands. A Bluetooth fountain sends its settings only when its parent asks,
     * so an unreported one has no chip rather than an "off" it never said.
     */
    fun modes(device: Device): List<FountainMode> = listOfNotNull(
        smartMode(device),
        switchMode(device, DND_KEYS, FountainModeKind.DoNotDisturb),
        switchMode(device, LOCK_KEYS, FountainModeKind.ChildLock),
    )

    private fun smartMode(device: Device): FountainMode? {
        val entity = SMART_MODE_KEYS.firstNotNullOfOrNull { device.entity(it) }
            ?.takeIf { it.component == EntityComponent.Select && SMART in it.options && NORMAL in it.options }
            ?: return null
        val option = EntityWrites.selectedOption(entity) ?: return null
        return FountainMode(
            entity.key,
            FountainModeKind.Smart,
            on = option == SMART,
            onValue = EntityValue.Text(SMART),
            offValue = EntityValue.Text(NORMAL),
        )
    }

    private fun switchMode(device: Device, keys: List<String>, kind: FountainModeKind): FountainMode? {
        val entity = keys.firstNotNullOfOrNull { device.entity(it) }
            ?.takeIf { it.component == EntityComponent.Switch }
            ?: return null
        val on = entity.value.asBoolean ?: return null
        return FountainMode(entity.key, kind, on, EntityValue.Bool(true), EntityValue.Bool(false))
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
