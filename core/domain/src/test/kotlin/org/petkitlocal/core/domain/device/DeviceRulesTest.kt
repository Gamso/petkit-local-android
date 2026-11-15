package org.petkitlocal.core.domain.device

import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.DeviceCommandRepository

class DeviceRulesTest {
    private val volume = entity("volume", EntityComponent.Number, EntityValue.Number(5.0), min = 1.0, max = 9.0)
    private val mode = entity("flow_mode", EntityComponent.Select, EntityValue.Number(1.0), display = "continuous")
        .copy(options = listOf("do_not_flow", "continuous", "intermittent"))
    private val lock = entity("child_lock", EntityComponent.Switch, EntityValue.Bool(false))
    private val refill = entity("water_change_time", EntityComponent.Time, EntityValue.Number(36000.0))
    private val quiet = entity("refill_disturb_period", EntityComponent.Text, EntityValue.Text("22:00-07:00"))
        .copy(category = EntityCategory.Config)

    @Test
    fun `each component takes its own type, within its bounds`() {
        assertNull(EntityWrites.problem(lock, EntityValue.Bool(true)))
        assertEquals(EntityWriteProblem.WrongType, EntityWrites.problem(lock, EntityValue.Text("on")))

        assertNull(EntityWrites.problem(volume, EntityValue.Number(9.0)))
        assertEquals(EntityWriteProblem.BelowMin(1.0), EntityWrites.problem(volume, EntityValue.Number(0.0)))
        assertEquals(EntityWriteProblem.AboveMax(9.0), EntityWrites.problem(volume, EntityValue.Number(10.0)))
        assertEquals(EntityWriteProblem.WrongType, EntityWrites.problem(volume, EntityValue.Number(Double.NaN)))

        assertNull(EntityWrites.problem(mode, EntityValue.Text("intermittent")))
        assertEquals(EntityWriteProblem.NotAnOption, EntityWrites.problem(mode, EntityValue.Text("turbo")))

        assertNull(EntityWrites.problem(refill, EntityValue.Text("07:30")))
        assertEquals(EntityWriteProblem.InvalidTime, EntityWrites.problem(refill, EntityValue.Text("25:00")))
        assertEquals(EntityWriteProblem.InvalidTime, EntityWrites.problem(refill, EntityValue.Text("7:30")))

        assertNull(EntityWrites.problem(quiet, EntityValue.Text("")))
        assertEquals(EntityWriteProblem.TooLong(255), EntityWrites.problem(quiet, EntityValue.Text("x".repeat(256))))
    }

    @Test
    fun `a read-only entity has no editor`() {
        val level = entity("litter_percent", EntityComponent.Sensor, EntityValue.Number(80.0)).copy(settable = false)

        assertEquals(EntityWriteProblem.NotSettable, EntityWrites.problem(level, EntityValue.Number(1.0)))
        assertEquals(
            EntityWriteProblem.NotSettable,
            EntityWrites.problem(lock.copy(settable = false), EntityValue.Bool(true)),
        )
    }

    @Test
    fun `a number snaps to its step from its minimum, within bounds`() {
        val interval = entity("ctw3_energy_interval", EntityComponent.Number, EntityValue.Number(60.0))
            .copy(min = 15.0, max = 3600.0, step = 15.0)
        val ratio = entity("ratio", EntityComponent.Number, EntityValue.Number(0.5)).copy(step = 0.1)

        assertEquals(60.0, EntityWrites.snap(interval, 67.0))
        assertEquals(75.0, EntityWrites.snap(interval, 68.0))
        assertEquals(15.0, EntityWrites.snap(interval, -40.0))
        assertEquals(3600.0, EntityWrites.snap(interval, 9999.0))
        assertEquals(0.3, EntityWrites.snap(ratio, 0.1 + 0.2))
        assertEquals(1.0, EntityWrites.step(volume))
    }

    @Test
    fun `a time is read from seconds or text, and written as HH_MM`() {
        assertEquals(LocalTime.of(10, 0), EntityWrites.timeOfDay(EntityValue.Number(36000.0)))
        assertEquals(LocalTime.of(7, 5, 9), EntityWrites.timeOfDay(EntityValue.Text("07:05:09")))
        assertNull(EntityWrites.timeOfDay(EntityValue.Number(86400.0)))
        assertNull(EntityWrites.timeOfDay(EntityValue.None))
        assertEquals("07:05", EntityWrites.timeText(LocalTime.of(7, 5, 9)))
    }

    @Test
    fun `a select is on its display label, or on a text value among its options`() {
        assertEquals("continuous", EntityWrites.selectedOption(mode))
        assertEquals(
            "intermittent",
            EntityWrites.selectedOption(mode.copy(display = null, value = EntityValue.Text("intermittent"))),
        )
        assertNull(EntityWrites.selectedOption(mode.copy(display = "gone")))
    }

    @Test
    fun `sections put every entity in one place, settings by category in order`() {
        val temperature = entity("temperature", EntityComponent.Sensor, EntityValue.Number(24.5)).copy(settable = false)
        val rssi = entity("rssi", EntityComponent.Sensor, EntityValue.Number(-54.0))
            .copy(settable = false, category = EntityCategory.Diagnostic)
        val debug = lock.copy(key = "debug_log", category = EntityCategory.Diagnostic)
        // Settable, but no editor for its component: shown as a reading.
        val gauge = entity("gauge", EntityComponent.Unknown, EntityValue.Number(1.0))
        val device = device(temperature, quiet, lock, rssi, debug, volume, gauge)

        val sections = DeviceSections.of(device)

        assertEquals(listOf("temperature", "gauge"), sections.readings.map { it.key })
        assertEquals(listOf("rssi"), sections.diagnostics.map { it.key })
        assertEquals(
            listOf(
                SettingCategory.ComfortAndSafety to listOf("child_lock"),
                SettingCategory.Sound to listOf("volume"),
                SettingCategory.DisplayAndQuiet to listOf("refill_disturb_period"),
                SettingCategory.Diagnostics to listOf("debug_log"),
            ),
            sections.settings.map { it.category to it.entities.map(Entity::key) },
        )
        assertEquals(4, sections.settingsCount)
    }

    @Test
    fun `a destructive action is sent only once confirmed, and then with confirm`() = runTest {
        val commands = RecordingCommands()
        val run = RunDeviceActionUseCase(commands)
        val drain = DeviceAction("fountain_drain", "Drain", destructive = true)
        val flush = DeviceAction("fountain_flush", "Flush", destructive = false)

        val refused = run(3001, drain, confirmed = false)
        run(3001, drain, confirmed = true)
        run(3001, flush, confirmed = false)

        assertEquals(
            Outcome.Failure(DomainError.Validation(null, RunDeviceActionUseCase.CONFIRMATION_REQUIRED, "")),
            refused,
        )
        assertEquals(listOf("fountain_drain" to true, "fountain_flush" to false), commands.actions)
    }

    @Test
    fun `a write the server would refuse is refused before any request`() = runTest {
        val commands = RecordingCommands()
        val set = SetEntityValueUseCase(commands)

        val refused = set(3001, volume, EntityValue.Number(12.0))
        val sent = set(3001, volume, EntityValue.Number(7.0))

        assertEquals(Outcome.Failure(DomainError.Validation("volume", "above_max", "")), refused)
        assertEquals(Outcome.Success(CommandDelivery.Queued), sent)
        assertEquals(listOf("volume" to EntityValue.Number(7.0)), commands.writes)
    }

    private class RecordingCommands : DeviceCommandRepository {
        val actions = mutableListOf<Pair<String, Boolean>>()
        val writes = mutableListOf<Pair<String, EntityValue>>()

        override suspend fun runAction(deviceId: Long, key: String, confirm: Boolean): Outcome<CommandDelivery> {
            actions += key to confirm
            return Outcome.Success(CommandDelivery.Mqtt)
        }

        override suspend fun setEntity(deviceId: Long, key: String, value: EntityValue): Outcome<CommandDelivery> {
            writes += key to value
            return Outcome.Success(CommandDelivery.Queued)
        }
    }

    private fun entity(
        key: String,
        component: EntityComponent,
        value: EntityValue,
        display: String? = null,
        min: Double? = null,
        max: Double? = null,
    ) = Entity(key, key, component, value, display = display, settable = true, min = min, max = max)

    private fun device(vararg entities: Entity) = Device(
        id = 3001,
        type = "w7h",
        model = "EverSweet Ultra AI",
        family = DeviceFamily.Fountain,
        online = true,
        transport = DeviceTransport.Mqtt,
        lastSeen = null,
        firmware = null,
        isCamera = true,
        entities = entities.toList(),
    )
}
