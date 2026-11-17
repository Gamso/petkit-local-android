package org.petkitlocal.feature.device.fountain

import java.time.Instant
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.fountain.FountainPage
import org.petkitlocal.core.domain.fountain.FountainRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
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
 * Fountains as the mock serves them (mock-server/README.md), with the entity and action keys
 * petkit-local defines: the EverSweet Ultra AI (W7H, on mains) with its drinks and its flow mode,
 * the EverSweet 3 Pro (W5) relayed by the YumShare Dual-Hopper, and an EverSweet Max Cordless
 * (CTW3, on battery) in trouble. Each has a settable mode, so each page has the mode selector.
 */
object FountainFixtures {
    val actionsW7h = listOf(
        DeviceAction("fountain_flush", "Drain & Flush", false),
        DeviceAction("fountain_refill", "Refill", false),
        DeviceAction("fountain_reset_circulation_pump", "Reset Circulation Pump", false),
        DeviceAction("fountain_drain", "Drain", true),
        DeviceAction("fountain_deep_clean", "Deep Clean", true),
    )

    val w7h = Household.device(
        3001,
        "w7h",
        "EverSweet Ultra AI",
        DeviceFamily.Fountain,
        sensor("error", EntityValue.None),
        sensor("temperature", EntityValue.Number(24.5), unit = "°C"),
        binary("tray_full", false, "problem", "Tray Full"),
        binary("pump_running", true, "running", "Circulation Pump"),
        diagnostic("tray_installed", true),
        diagnostic("clean_tank_installed", true),
        diagnostic("waste_tank_installed", true),
        switch("child_lock", "Child Lock", false),
        Entity(
            "flow_mode",
            "Flow Mode",
            EntityComponent.Select,
            EntityValue.Number(1.0),
            display = "continuous",
            options = listOf("do_not_flow", "continuous", "intermittent", "motion_activated"),
            settable = true,
        ),
        transport = DeviceTransport.Mqtt,
        isCamera = true,
        actions = actionsW7h,
    ).copy(lastSeen = Instant.parse("2026-11-15T08:58:00Z"), firmware = "1.263")

    val relay = Household.dualFeeder

    /** A W5 relayed by the YumShare Dual-Hopper, its last frame three hours old. */
    val w5 = Household.device(
        3002,
        "w5",
        "EverSweet 3 Pro",
        DeviceFamily.Fountain,
        sensor("w5_filter", EntityValue.Number(64.0), unit = "%", name = "Filter"),
        binary("w5_water_missing", false, "problem", "Water Low"),
        binary("w5_filter_warn", false, "problem", "Filter Warning"),
        binary("w5_running", true, "running", "Pump Running"),
        mode("w5_mode", "normal"),
        switch("w5_dnd", "Do Not Disturb", false),
        switch("w5_child_lock", "Child Lock", false),
        sensor("w5_pump_today", EntityValue.Number(14_400.0), unit = "s", category = EntityCategory.Diagnostic),
        transport = DeviceTransport.Mqtt,
        actions = listOf(DeviceAction("w5_reset_filter", "Reset Filter", true)),
        connection = DeviceConnection.Ble,
        parentId = 2004,
    ).copy(lastSeen = Instant.parse("2026-11-15T05:53:00Z"))

    /** A CTW3 low on water, filter and battery, its pump at rest, in smart mode, do not disturb on. */
    val ctw3 = Household.device(
        3003,
        "ctw3",
        "EverSweet Max Cordless",
        DeviceFamily.Fountain,
        sensor("ctw3_filter", EntityValue.Number(12.0), unit = "%", name = "Filter"),
        sensor("ctw3_battery", EntityValue.Number(9.0), unit = "%", name = "Battery"),
        binary("ctw3_water_lack", true, "problem", "Water Low"),
        binary("ctw3_running", false, "running", "Pump Running"),
        binary("ctw3_low_battery", true, "battery", "Low Battery"),
        mode("ctw3_mode", "smart"),
        switch("ctw3_dnd", "Do Not Disturb", true),
        switch("ctw3_child_lock", "Child Lock", false),
        sensor("ctw3_pump_today", EntityValue.Number(2_100.0), unit = "s", category = EntityCategory.Diagnostic),
        actions = listOf(DeviceAction("ctw3_reset_filter", "Reset Filter", true)),
        connection = DeviceConnection.Ble,
        parentId = 2001,
    ).copy(lastSeen = Instant.parse("2026-11-15T08:41:00Z"))

    val todayItems: List<TimelineItem> = listOf(
        item(40, 3001, at(9, 30), TimelineCategory.Drinking, pet = 2, duration = 42),
        item(39, 1002, at(9, 0), TimelineCategory.Toileting, pet = 1, duration = 80),
        item(38, 3001, at(8, 12), TimelineCategory.Drinking, pet = 1, duration = 18),
        item(37, 3001, at(6, 40), TimelineCategory.Drinking, pet = 2, duration = 25),
        item(36, 3001, at(2, 5), TimelineCategory.Drinking, pet = 1, duration = 11),
        item(35, 3001, at(0, 50), TimelineCategory.Drinking, duration = 30),
    )

    val yesterdayItems: List<TimelineItem> = listOf(
        item(30, 3001, at(21, 0).minusSeconds(DAY), TimelineCategory.Drinking, pet = 1, duration = 20),
        item(29, 3001, at(15, 0).minusSeconds(DAY), TimelineCategory.Drinking, pet = 1, duration = 35),
        item(28, 3001, at(7, 0).minusSeconds(DAY), TimelineCategory.Drinking, pet = 2, duration = 30),
    )

    fun page(
        device: Device = w7h,
        today: List<TimelineItem> = todayItems,
        relay: Device? = null,
        yesterday: List<TimelineItem> = yesterdayItems,
    ): FountainPage = FountainRules.page(device, Household.pets, today, yesterday, Household.zone, relay)

    /** The page's clock: the household's "now", 10:00 in Paris. */
    val now: Instant = Household.clock.instant()

    val content = FountainUiState(page = page(), loaded = true, zone = Household.zone, now = now)

    val bluetooth = FountainUiState(page = page(w5, relay = relay), loaded = true, zone = Household.zone, now = now)

    /** The relay of [trouble]: the Fresh Element Solo, offline, so its fountain is too. */
    val offlineRelay = Household.feeder.copy(online = false)

    /** The CTW3 in trouble, its relay offline, the server out of reach, and a refused filter reset. */
    val trouble = FountainUiState(
        page = page(ctw3.copy(online = false), relay = offlineRelay),
        loaded = true,
        zone = Household.zone,
        now = now,
        refreshError = DomainError.Network("timeout"),
        commands = CommandState(
            feedback = CommandFeedback(
                CommandSubject.Action("ctw3_reset_filter", "Reset Filter"),
                CommandResult.Failed(DomainError.AccessoryUnreachable("Fresh Element Solo")),
                id = 1,
            ),
        ),
    )

    val sent = CommandResult.Sent(CommandDelivery.Mqtt)

    private const val DAY = 86_400L

    private fun switch(key: String, name: String, on: Boolean) =
        Entity(key, name, EntityComponent.Switch, EntityValue.Bool(on), settable = true)

    private fun diagnostic(key: String, on: Boolean) = Entity(
        key,
        key,
        EntityComponent.BinarySensor,
        EntityValue.Bool(on),
        category = EntityCategory.Diagnostic,
    )

    /** A mode select reading [option], as petkit-local renders `states.mode` (1 normal, 2 smart). */
    private fun mode(key: String, option: String) = Entity(
        key,
        "Mode",
        EntityComponent.Select,
        EntityValue.Number(if (option == "smart") 2.0 else 1.0),
        display = option,
        options = listOf("normal", "smart"),
        settable = true,
    )
}
