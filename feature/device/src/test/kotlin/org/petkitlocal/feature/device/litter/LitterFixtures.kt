package org.petkitlocal.feature.device.litter

import java.time.Instant
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.litter.LitterBoxPage
import org.petkitlocal.core.domain.litter.LitterRules
import org.petkitlocal.core.domain.model.AccessoryRef
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineStep
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.Household.at
import org.petkitlocal.core.testing.Household.binary
import org.petkitlocal.core.testing.Household.item
import org.petkitlocal.core.testing.Household.sensor
import org.petkitlocal.feature.device.common.CommandFeedback
import org.petkitlocal.feature.device.common.CommandResult
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.CommandSubject

/**
 * Litter boxes as the mock serves them (mock-server/README.md): a Pura Max (T4) and a Purobot
 * Ultra (T6) with the entity and action keys petkit-local defines, and their cards; the Pura Max
 * also with the Pura Air (K3) the mock links to it.
 */
object LitterFixtures {
    val actionsT4 = listOf(
        DeviceAction("cleaning_start", "Start Cleaning", false),
        DeviceAction("maintenance_stop", "Exit Maintenance", false),
        DeviceAction("deodorize", "Deodorize", false),
        DeviceAction("pause", "Pause", false),
        DeviceAction("resume", "Resume", false),
        DeviceAction("reset", "Reset", false),
        DeviceAction("level_litter", "Level Litter", false),
        DeviceAction("maintenance_start", "Enter Maintenance", true),
        DeviceAction("dump_litter", "Dump Litter", true),
        DeviceAction("reset_n50", "Reset N50", true),
    )

    val pura = Household.device(
        1002,
        "t4",
        "Pura Max",
        DeviceFamily.LitterBox,
        sensor("device_status", EntityValue.Number(-1.0), display = "idle"),
        sensor("error", EntityValue.None),
        sensor("litter_percent", EntityValue.Number(91.0), unit = "%"),
        sensor("n50_durability", EntityValue.Number(21.0), unit = "days"),
        binary("waste_bin", false, "problem", "Waste Bin Full"),
        binary("waste_bin_present", true, "presence"),
        binary("pet_occupied", false, "occupancy"),
        switch("auto_work", "Auto Clean", true),
        switch("child_lock", "Child Lock", false),
        actions = actionsT4,
    )

    /** A T6 in trouble: bin full, spray missing, litter low, its error showing. */
    val ultra = Household.device(
        1004,
        "t6",
        "Purobot Ultra",
        DeviceFamily.LitterBox,
        sensor("device_status", EntityValue.Number(-1.0), display = "idle"),
        sensor("error", EntityValue.Text("bin full")),
        sensor("litter_percent", EntityValue.Number(12.0), unit = "%"),
        sensor("n60_spray_days", EntityValue.Number(17.0), unit = "days"),
        binary("waste_bin", true, "problem"),
        binary("waste_bin_present", true, "presence"),
        binary("deodorizer_present", false, "presence"),
        sensor("package_count", EntityValue.Number(7.0)),
        switch("auto_work", "Auto Clean", true),
        switch("auto_spray", "Auto Deodorizing", false),
        transport = DeviceTransport.Mqtt,
        isCamera = true,
        actions = actionsT4.filter { it.key != "reset_n50" } +
            DeviceAction("reset_n60", "Reset N60", true) +
            DeviceAction("pack_waste", "Pack Waste", false),
    )

    /** The Pura Max holding the household's Pura Air (63 % of N60) in its K3 slot. */
    val puraWithK3 = pura.copy(accessories = listOf(AccessoryRef(4001, "k3", DeviceFamily.Purifier)))

    val puraAir: Device = Household.purifier

    // Lazy: the page reads the day's cards, declared below.
    val withPuraAir by lazy {
        LitterUiState(page = page(puraWithK3, puraAir = puraAir), loaded = true, zone = Household.zone)
    }

    val todayItems: List<TimelineItem> = listOf(
        item(16, 1002, at(9, 45), TimelineCategory.Toileting, pet = 1, duration = 95).copy(
            weightG = 4310,
            steps = listOf(
                step(161, at(9, 45), "Weight check", detail = true),
                step(162, at(9, 46), "Mechanism started", detail = true),
                step(163, at(9, 47), "Auto cleaning completed"),
                step(164, at(9, 48), "Auto deodorizing completed"),
            ),
        ),
        item(14, 1002, at(6, 19), TimelineCategory.Toileting, pet = 2, duration = 186).copy(
            weightG = 6065,
            steps = listOf(
                step(141, at(6, 21), "Auto cleaning completed"),
                step(142, at(6, 22), "Auto deodorizing failed"),
            ),
        ),
        item(13, 1002, at(5, 10), TimelineCategory.Pet).copy(eventKind = "pet", label = "Appeared"),
        item(11, 1002, at(3, 0), TimelineCategory.Cleaning).copy(
            eventKind = "cleaning",
            label = "Periodic cleaning completed",
        ),
        item(10, 1002, at(1, 20), TimelineCategory.Toileting, pet = 2, duration = 60).copy(
            weightG = 6010,
            steps = listOf(step(101, at(1, 22), "Auto cleaning stopped (bin full)")),
        ),
        item(9, 1002, at(0, 40), TimelineCategory.Toileting, duration = 40),
    )

    val yesterdayItems: List<TimelineItem> = listOf(
        item(5, 1002, at(22, 0).minusSeconds(DAY), TimelineCategory.Toileting, pet = 1, duration = 100),
        item(4, 1002, at(12, 0).minusSeconds(DAY), TimelineCategory.Toileting, pet = 2, duration = 140),
    )

    /** The household, Mochi with a photo (drawn as a placeholder in screenshots), Tigrou without. */
    val pets = Household.pets.map {
        if (it.id ==
            1L
        ) {
            it.copy(photoUrl = "https://petkit.invalid/app/v1/pets/1/photo")
        } else {
            it
        }
    }

    fun page(device: Device = pura, today: List<TimelineItem> = todayItems, puraAir: Device? = null): LitterBoxPage =
        LitterRules.page(device, pets, today, yesterdayItems, Household.zone, puraAir)

    val content = LitterUiState(page = page(), loaded = true, zone = Household.zone)

    val trouble = LitterUiState(
        page = page(ultra, emptyList()),
        loaded = true,
        zone = Household.zone,
        refreshError = DomainError.Network("timeout"),
        commands = CommandState(
            feedback = CommandFeedback(
                CommandSubject.Action("pack_waste", "Pack Waste"),
                CommandResult.Sent(CommandDelivery.Mqtt),
                id = 1,
            ),
        ),
    )

    val loading = LitterUiState(zone = Household.zone)

    val gone = LitterUiState(loaded = true, zone = Household.zone)

    private const val DAY = 86_400L

    private fun step(id: Long, at: Instant, label: String, detail: Boolean = false) =
        TimelineStep(id, at, label, detail)

    private fun switch(key: String, name: String, on: Boolean) =
        Entity(key, name, EntityComponent.Switch, EntityValue.Bool(on), settable = true)
}
