package org.petkitlocal.feature.device

import java.time.Instant
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.device.DeviceSections
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.ui.RecentClipsState
import org.petkitlocal.feature.device.common.CommandFeedback
import org.petkitlocal.feature.device.common.CommandResult
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.CommandSubject
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.petkitlocal.feature.device.generic.GenericDeviceUiState
import org.petkitlocal.feature.device.settings.DeviceSettingsUiState
import org.petkitlocal.feature.device.settings.SettingEditorState

/** Devices as the mock serves them (mock-server catalog), for the device screens' tests. */
object DeviceFixtures {
    /** A camera's recent clips, as its page shows them once the server answered. */
    val recentClips = RecentClipsUi(
        RecentClipsState(
            clips = Household.cameraClips,
            cacheLoaded = true,
            refreshed = true,
            zone = Household.zone,
            today = Household.today,
        ),
    )

    val flush = DeviceAction("fountain_flush", "Drain & Flush", destructive = false)
    val refill = DeviceAction("fountain_refill", "Refill", destructive = false)
    val drain = DeviceAction("fountain_drain", "Drain", destructive = true)

    val childLock = Entity(
        "child_lock",
        "Child Lock",
        EntityComponent.Switch,
        EntityValue.Bool(false),
        settable = true,
    )
    val fountainTime = Entity(
        "fountain_time",
        "Fountain Time",
        EntityComponent.Number,
        EntityValue.Number(3.0),
        unit = "min",
        settable = true,
        min = 1.0,
        max = 60.0,
        step = 1.0,
    )
    val flowMode = Entity(
        "flow_mode",
        "Flow Mode",
        EntityComponent.Select,
        EntityValue.Number(1.0),
        display = "continuous",
        options = listOf("do_not_flow", "continuous", "intermittent", "motion_activated"),
        settable = true,
    )
    val refillTime = Entity(
        "water_change_time",
        "Drain & Refill Time",
        EntityComponent.Time,
        EntityValue.Number(36_000.0),
        settable = true,
    )
    val quietHours = Entity(
        "refill_disturb_period",
        "Quiet Refill Hours",
        EntityComponent.Text,
        EntityValue.Text("22:00-07:00"),
        category = EntityCategory.Config,
        settable = true,
    )

    /** An EverSweet Ultra AI: readings, settings of every component, a destructive action. */
    val fountain = Device(
        id = 3001,
        type = "w7h",
        model = "EverSweet Ultra AI",
        family = DeviceFamily.Fountain,
        online = true,
        transport = DeviceTransport.Mqtt,
        lastSeen = Instant.parse("2026-11-15T09:58:00Z"),
        firmware = "1.263",
        isCamera = true,
        serial = "20250610W7H00077",
        entities = listOf(
            Entity("error", "Error", EntityComponent.Sensor, EntityValue.None),
            Entity("temperature", "Water Temperature", EntityComponent.Sensor, EntityValue.Number(24.5), unit = "°C"),
            Entity("last_drink", "Last Drink", EntityComponent.Sensor, EntityValue.Text("2026-11-15T07:30:15+00:00")),
            Entity(
                "deep_clean_stage",
                "Deep Clean Stage",
                EntityComponent.Sensor,
                EntityValue.Number(0.0),
                display = "idle",
            ),
            Entity("tray_full", "Tray Full", EntityComponent.BinarySensor, EntityValue.Bool(false)),
            Entity(
                "rssi",
                "WiFi Signal",
                EntityComponent.Sensor,
                EntityValue.Number(-54.0),
                unit = "dBm",
                category = EntityCategory.Diagnostic,
            ),
            childLock,
            fountainTime,
            flowMode,
            refillTime,
            quietHours,
        ),
        actions = listOf(flush, refill, drain),
    )

    /** A Pura Max with the actions the mock gives a T4, in the server's order. */
    val litterBox = Household.litterBox.copy(
        actions = listOf(
            DeviceAction("cleaning_start", "Start Cleaning", false),
            DeviceAction("maintenance_stop", "Exit Maintenance", false),
            DeviceAction("deodorize", "Deodorize", false),
            DeviceAction("pause", "Pause", false),
            DeviceAction("resume", "Resume", false),
            DeviceAction("reset", "Reset", false),
            DeviceAction("level_litter", "Level Litter", false),
            DeviceAction("finish_litter_calibration", "Finish Litter Calibration", false),
            DeviceAction("maintenance_start", "Start Maintenance", true),
            DeviceAction("dump_litter", "Dump Litter", true),
            DeviceAction("reset_n50", "Reset N50", true),
        ),
    )

    /** The Pura Max's settings, as the mock serves a T4's (values from its seed). */
    val litterSettings = litterBox.copy(
        entities = listOf(
            setting("auto_work", "Auto Clean", EntityValue.Bool(true)),
            Entity(
                "cleaning_delay",
                "Cleaning Delay",
                EntityComponent.Number,
                EntityValue.Number(120.0),
                unit = "s",
                settable = true,
                min = 0.0,
                max = 3600.0,
            ),
            setting("avoid_repeat", "Avoid Repeat Clean", EntityValue.Bool(true)),
            Entity(
                "cleaning_interval",
                "Avoid Repeat Interval",
                EntityComponent.Select,
                EntityValue.Number(1800.0),
                display = "30min",
                options = listOf("disabled", "5min", "10min", "15min", "30min", "45min", "1h"),
                settable = true,
            ),
            setting("fixed_time_clear", "Periodic Cleaning", EntityValue.Bool(false)),
            setting("deep_clean", "Deep Cleaning", EntityValue.Bool(false)),
            setting("downpos", "Continuous Rotation", EntityValue.Bool(false)),
            Entity(
                "sand_type",
                "Litter Type",
                EntityComponent.Select,
                EntityValue.Number(1.0),
                display = "bentonite",
                options = listOf("bentonite", "tofu", "mixed"),
                settable = true,
            ),
            setting("sand_saving", "Litter Saving", EntityValue.Bool(false)),
            setting("bury", "Waste Covering", EntityValue.Bool(false)),
            setting("kitten", "Kitten Mode", EntityValue.Bool(false)),
            setting("underweight", "Underweight Detection", EntityValue.Bool(true)),
            setting("child_lock", "Child Lock", EntityValue.Bool(false)),
            setting("click_ok", "OK Button Under Child Lock", EntityValue.Bool(true), EntityCategory.Config),
            setting("display", "Display", EntityValue.Bool(true)),
            setting("disturb_mode", "Do Not Disturb", EntityValue.Bool(false)),
            Entity(
                "cleaning_schedule",
                "Cleaning Schedule (JSON)",
                EntityComponent.Text,
                EntityValue.Text("[]"),
                category = EntityCategory.Config,
                settable = true,
            ),
        ),
    )

    private fun setting(key: String, name: String, value: EntityValue, category: EntityCategory? = null) =
        Entity(key, name, EntityComponent.Switch, value, category = category, settable = true)

    /** A Pura Air K3: two levels, no setting, no action; relayed by the Pura Max. */
    val purifier = Household.purifier.copy(serial = "K3-0001")

    /** The Pura Max relaying it. */
    val puraMax = Household.litterBox

    val zone = Household.zone

    fun generic(device: Device = fountain, commands: CommandState = CommandState(), offline: Boolean = false) =
        GenericDeviceUiState(
            loaded = true,
            device = device,
            status = HomeRules.status(device),
            sections = DeviceSections.of(device),
            offline = offline,
            commands = commands,
            zone = zone,
        )

    fun settings(
        editor: SettingEditorState? = null,
        commands: CommandState = CommandState(),
        device: Device = fountain,
    ) = DeviceSettingsUiState(
        loaded = true,
        device = device,
        groups = DeviceSections.of(device).settings,
        editor = editor,
        commands = commands,
        zone = zone,
    )

    val sent = CommandState(
        feedback = CommandFeedback(
            CommandSubject.Action(flush.key, flush.name),
            CommandResult.Sent(CommandDelivery.Queued),
            1,
        ),
    )

    val refused = CommandState(
        feedback = CommandFeedback(
            CommandSubject.Setting(fountainTime.key, fountainTime.name),
            CommandResult.Failed(DomainError.Validation(null, "refused", "Fountain Time must be between 1 and 60")),
            2,
        ),
    )

    val tooLong = SettingEditorState(fountainTime, problem = EntityWriteProblem.AboveMax(60.0))
}
