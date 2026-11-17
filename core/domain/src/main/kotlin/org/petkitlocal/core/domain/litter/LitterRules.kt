package org.petkitlocal.core.domain.litter

import java.time.ZoneId
import kotlin.math.roundToInt
import org.petkitlocal.core.domain.activity.ActivityRules
import org.petkitlocal.core.domain.devicepage.HotspotRules
import org.petkitlocal.core.domain.devicepage.LitterParts
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.asBoolean

/**
 * The rules of the litter box page. Like Home's, they read entities by stable key and the
 * timeline by category, never by model.
 */
object LitterRules {
    /** The switches shown as mode chips, in this order, when the device has them. */
    val MODE_KEYS = listOf("auto_work", "auto_spray")

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    /**
     * @param today the cards of the local day shown, any device; [yesterday] those of the day before.
     * @param zone the phone's time zone, which places a visit on the chart.
     * @param puraAir the Pura Air paired with [device], if known (see [LitterParts]).
     */
    @Suppress("LongParameterList") // The page's sources, the zone and the paired Pura Air.
    fun page(
        device: Device,
        pets: List<Pet>,
        today: List<TimelineItem>,
        yesterday: List<TimelineItem>,
        zone: ZoneId,
        puraAir: Device? = null,
    ): LitterBoxPage {
        val mine = today.filter { it.deviceId == device.id }
        return LitterBoxPage(
            device = device,
            status = HomeRules.status(device),
            modes = modes(device),
            hotspots = HotspotRules.litterBox(device, puraAir),
            actions = LitterParts.actions(device, puraAir),
            today = visitStats(mine),
            yesterday = visitStats(yesterday.filter { it.deviceId == device.id }),
            visits = visits(mine, zone),
            pets = pets,
            activity = ActivityRules.entries(mine, pets),
            puraAir = puraAir,
        )
    }

    fun modes(device: Device): List<DeviceMode> = MODE_KEYS.mapNotNull { key ->
        val entity = device.entity(key)?.takeIf { it.component == EntityComponent.Switch } ?: return@mapNotNull null
        DeviceMode(key, entity.name, on = entity.value.asBoolean == true)
    }

    /** Toilet visits among [items] (one box's cards of one day), and their average duration. */
    fun visitStats(items: List<TimelineItem>): VisitStats {
        val visits = items.filter { it.isToiletVisit }
        val durations = visits.mapNotNull { it.durationSec }
        return VisitStats(visits.size, durations.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
    }

    fun visits(items: List<TimelineItem>, zone: ZoneId): List<Visit> = items
        .filter { it.isToiletVisit }
        .mapNotNull { item ->
            val local = (item.shownAt ?: return@mapNotNull null).atZone(zone).toLocalTime()
            Visit(
                itemId = item.id,
                minuteOfDay = (local.hour * MINUTES_PER_HOUR + local.minute).coerceIn(0, MINUTES_PER_DAY - 1),
                durationSec = item.durationSec,
                petId = item.pet?.id,
            )
        }
        .sortedBy { it.minuteOfDay }

    private val TimelineItem.isToiletVisit: Boolean get() = TimelineCategory.Toileting in categories
}
