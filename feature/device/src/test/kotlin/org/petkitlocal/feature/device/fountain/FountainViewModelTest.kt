package org.petkitlocal.feature.device.fountain

import androidx.lifecycle.SavedStateHandle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.DrinkSyncFailure
import org.petkitlocal.core.domain.drinksync.DrinkSyncStep
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure
import org.petkitlocal.core.domain.drinksync.ObserveLastDrinkSyncUseCase
import org.petkitlocal.core.domain.drinksync.SyncDrinksUseCase
import org.petkitlocal.core.domain.fountain.ObserveFountainUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.testing.FakeDeviceCommandRepository
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeDrinkSyncRepository
import org.petkitlocal.core.testing.FakeFountainBluetooth
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.common.CommandResult

@OptIn(ExperimentalCoroutinesApi::class)
class FountainViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val devices =
        FakeDeviceRepository(listOf(FountainFixtures.w7h, FountainFixtures.w5, FountainFixtures.relay))
    private val pets = FakePetRepository(Household.pets)
    private val timeline = FakeTimelineRepository().apply {
        days.value = mapOf(
            Household.today to FountainFixtures.todayItems,
            Household.today.minusDays(1) to FountainFixtures.yesterdayItems,
        )
    }
    private val live = FakeLiveUpdatesRepository()
    private val commands = FakeDeviceCommandRepository().apply { result = Outcome.Success(CommandDelivery.Mqtt) }
    private val drinkSync = FakeDrinkSyncRepository()
    private val bluetooth = FakeFountainBluetooth()
    private var bluetoothGranted = true

    private fun viewModel(id: Long = 3001L) = FountainViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to id)),
        ObserveFountainUseCase(devices, pets, timeline, Household.clock),
        ObserveLiveUpdatesUseCase(live),
        RefreshDevicePageUseCase(devices, pets, timeline),
        RunDeviceActionUseCase(commands),
        SetEntityValueUseCase(commands),
        TodayUseCase(Household.clock),
        Household.clock,
        SyncDrinksUseCase(drinkSync, bluetooth, timeline, Household.clock),
        ObserveLastDrinkSyncUseCase(drinkSync),
        { bluetoothGranted },
    )

    private fun TestScope.collect(viewModel: FountainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `shows the cached fountain at once and refreshes it, today and yesterday, on opening`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { devices.gate = it }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.loaded)
            assertTrue(state.refreshing)
            assertEquals("EverSweet Ultra AI", state.page?.device?.modelName)
            assertEquals(5, state.page?.today?.drinks)
            assertEquals(3, state.page?.yesterday?.drinks)
            // Shown, so the live socket is kept open.
            assertEquals(1, live.collectors.value)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.refreshing)
            assertEquals(setOf(Household.today, Household.today.minusDays(1)), timeline.refreshedDays.toSet())
        }

    @Test
    fun `a Bluetooth fountain names its relay, and follows a report the relay brings`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()

        val page = viewModel.uiState.value.page!!
        assertEquals("YumShare Dual-Hopper", page.relay?.modelName)
        assertFalse(page.recordsDrinks)
        assertEquals(14_400, page.pumpTodaySec)

        val reset = FountainFixtures.w5.copy(
            entities = FountainFixtures.w5.entities.map {
                if (it.key == "w5_filter") it.copy(value = EntityValue.Number(100.0)) else it
            },
        )
        devices.devices.value = listOf(FountainFixtures.w7h, reset, FountainFixtures.relay)
        runCurrent()
        val filter = viewModel.uiState.value.page!!.hotspots.single { it.part == DevicePart.Filter }
        assertEquals(PartReading.Percent(100), filter.reading)
    }

    @Test
    fun `the filter reset waits for confirmation, then sends confirm`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()
        val reset = viewModel.uiState.value.page!!.actions.single()

        viewModel.onHotspot(DevicePart.Filter)
        viewModel.onAction(reset)
        runCurrent()
        // The explanation closes; the confirmation opens; nothing is sent yet.
        assertNull(viewModel.uiState.value.openPart)
        assertEquals("w5_reset_filter", viewModel.uiState.value.commands.confirming?.key)
        assertTrue(commands.actions.isEmpty())

        viewModel.onConfirmAction()
        runCurrent()
        assertEquals(
            listOf(FakeDeviceCommandRepository.Action(3002, "w5_reset_filter", confirm = true)),
            commands.actions,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback?.result)
    }

    @Test
    fun `a plain action is sent at once and changes nothing until the device reports`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(FountainFixtures.actionsW7h.first { it.key == "fountain_refill" })
        runCurrent()

        assertEquals(
            listOf(FakeDeviceCommandRepository.Action(3001, "fountain_refill", confirm = false)),
            commands.actions,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback?.result)
    }

    /** The W5 as the fountain would report it in [option]: `states.mode` 1 normal, 2 smart. */
    private fun w5In(option: String) = FountainFixtures.w5.copy(
        entities = FountainFixtures.w5.entities.map {
            if (it.key == "w5_mode") {
                it.copy(value = EntityValue.Number(if (option == "smart") 2.0 else 1.0), display = option)
            } else {
                it
            }
        },
    )

    @Test
    fun `a mode tapped is sent and pending, the selection moving only when the fountain reports it`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel(3002L)
            collect(viewModel)
            advanceUntilIdle()
            assertEquals("normal", viewModel.uiState.value.page?.modeBar?.selected)

            viewModel.onMode("smart")
            runCurrent()

            assertEquals(
                listOf(FakeDeviceCommandRepository.Write(3002, "w5_mode", EntityValue.Text("smart"))),
                commands.writes,
            )
            assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback?.result)
            // No optimistic UI: still normal, smart pending.
            assertEquals("normal", viewModel.uiState.value.page?.modeBar?.selected)
            assertEquals("smart", viewModel.uiState.value.pendingMode)

            // The relay brings the fountain's report: smart is selected, nothing pending.
            devices.devices.value = listOf(FountainFixtures.w7h, w5In("smart"), FountainFixtures.relay)
            runCurrent()
            assertEquals("smart", viewModel.uiState.value.page?.modeBar?.selected)
            assertNull(viewModel.uiState.value.pendingMode)
            assertNull(viewModel.uiState.value.requestedMode)

            // Back to normal by a button on the fountain itself: nothing pending reappears.
            devices.devices.value = listOf(FountainFixtures.w7h, w5In("normal"), FountainFixtures.relay)
            runCurrent()
            assertNull(viewModel.uiState.value.pendingMode)
        }

    @Test
    fun `a mode the server refuses is no longer pending, and says why`() = runTest(main.dispatcher) {
        commands.result = Outcome.Failure(DomainError.AccessoryUnreachable("YumShare Dual-Hopper"))
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onMode("smart")
        runCurrent()

        assertEquals(
            CommandResult.Failed(DomainError.AccessoryUnreachable("YumShare Dual-Hopper")),
            viewModel.uiState.value.commands.feedback?.result,
        )
        assertNull(viewModel.uiState.value.pendingMode)
        assertEquals("normal", viewModel.uiState.value.page?.modeBar?.selected)
    }

    @Test
    fun `a mode the fountain never reports stops being pending after a while`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onMode("smart")
        runCurrent()
        assertEquals("smart", viewModel.uiState.value.pendingMode)

        advanceTimeBy(FountainViewModel.MODE_CONFIRMATION_TIMEOUT_MS + 1)
        runCurrent()
        assertNull(viewModel.uiState.value.pendingMode)
        assertEquals("normal", viewModel.uiState.value.page?.modeBar?.selected)
    }

    @Test
    fun `taps while a mode is on its way, on the reported mode, or on no option, send nothing`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { commands.gate = it }
            val viewModel = viewModel(3002L)
            collect(viewModel)
            advanceUntilIdle()

            // The reported mode, nothing pending: nothing to send. Not an option: nothing either.
            viewModel.onMode("normal")
            viewModel.onMode("turbo")
            runCurrent()
            assertTrue(commands.writes.isEmpty())

            viewModel.onMode("smart")
            runCurrent()
            assertTrue("w5_mode" in viewModel.uiState.value.commands.sending)
            // In flight: a second tap is ignored.
            viewModel.onMode("normal")
            runCurrent()
            assertEquals(1, commands.writes.size)

            gate.complete(Unit)
            runCurrent()
            // Sent, still pending: going back to the reported mode is a write of its own.
            viewModel.onMode("normal")
            runCurrent()
            assertEquals(
                listOf(EntityValue.Text("smart"), EntityValue.Text("normal")),
                commands.writes.map { it.value },
            )
            assertNull(viewModel.uiState.value.pendingMode)
        }

    @Test
    fun `a W7H's flow mode is the bar, its four options in the server's order`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        val bar = viewModel.uiState.value.page?.modeBar
        assertEquals("flow_mode", bar?.key)
        assertEquals(listOf("do_not_flow", "continuous", "intermittent", "motion_activated"), bar?.options)
        assertEquals("continuous", bar?.selected)

        viewModel.onMode("intermittent")
        runCurrent()
        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(3001, "flow_mode", EntityValue.Text("intermittent"))),
            commands.writes,
        )
    }

    @Test
    fun `Pause pauses a running CTW3 and runs a paused one, as the server answers`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(FountainFixtures.ctw3, FountainFixtures.onlineRelay)
        val viewModel = viewModel(3003L)
        collect(viewModel)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.page?.modeBar?.pause?.paused)

        viewModel.onPause()
        runCurrent()
        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(3003, "ctw3_working", EntityValue.Bool(false))),
            commands.writes,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback?.result)
        // No optimistic UI: the tile follows the cache, not the tap.
        assertEquals(false, viewModel.uiState.value.page?.modeBar?.pause?.paused)

        devices.devices.value = listOf(FountainFixtures.ctw3(working = false), FountainFixtures.onlineRelay)
        runCurrent()
        viewModel.onPause()
        runCurrent()
        assertEquals(EntityValue.Bool(true), commands.writes.last().value)
        // The mode is left alone: Pause is its own switch.
        assertTrue(commands.writes.none { it.key == "ctw3_mode" })
    }

    @Test
    fun `on battery, a mode is sent all the same and the server's refusal says why`() = runTest(main.dispatcher) {
        val refusal = DomainError.Validation(field = null, code = "on_battery", message = "the fountain is on battery")
        commands.result = Outcome.Failure(refusal)
        devices.devices.value = listOf(FountainFixtures.ctw3(mode = 3), FountainFixtures.onlineRelay)
        val viewModel = viewModel(3003L)
        collect(viewModel)
        advanceUntilIdle()
        val bar = viewModel.uiState.value.page!!.modeBar!!
        assertNull(bar.selected)
        assertTrue(bar.battery!!.active)

        viewModel.onMode("normal")
        runCurrent()

        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(3003, "ctw3_mode", EntityValue.Text("normal"))),
            commands.writes,
        )
        assertEquals(CommandResult.Failed(refusal), viewModel.uiState.value.commands.feedback?.result)
        assertNull(viewModel.uiState.value.pendingMode)
        // Pause is not refused on battery.
        commands.result = Outcome.Success(CommandDelivery.Mqtt)
        viewModel.onPause()
        runCurrent()
        assertEquals(EntityValue.Bool(false), commands.writes.last().value)
    }

    @Test
    fun `the battery mode's window opens its times' editors, and comes back once one is saved`() =
        runTest(main.dispatcher) {
            devices.devices.value = listOf(FountainFixtures.ctw3(mode = 3), FountainFixtures.onlineRelay)
            val viewModel = viewModel(3003L)
            collect(viewModel)
            advanceUntilIdle()

            viewModel.onBatteryMode(true)
            viewModel.onEdit("ctw3_energy_interval")
            runCurrent()
            assertTrue(viewModel.uiState.value.batteryModeOpen)
            assertEquals("ctw3_energy_interval", viewModel.uiState.value.editor?.entity?.key)

            // Out of PetKit's range: said in place, nothing sent.
            viewModel.onSave("ctw3_energy_interval", EntityValue.Number(301.0))
            runCurrent()
            assertEquals(EntityWriteProblem.AboveMax(300.0), viewModel.uiState.value.editor?.problem)
            assertTrue(commands.writes.isEmpty())

            viewModel.onSave("ctw3_energy_interval", EntityValue.Number(120.0))
            runCurrent()
            assertEquals(
                listOf(FakeDeviceCommandRepository.Write(3003, "ctw3_energy_interval", EntityValue.Number(120.0))),
                commands.writes,
            )
            // Taken: the editor closes, back to the battery mode's window.
            assertNull(viewModel.uiState.value.editor)
            assertTrue(viewModel.uiState.value.batteryModeOpen)

            // Closing the window closes an editor open over it too.
            viewModel.onEdit("ctw3_sleep_time")
            viewModel.onBatteryMode(false)
            runCurrent()
            assertNull(viewModel.uiState.value.editor)
            assertFalse(viewModel.uiState.value.batteryModeOpen)
        }

    @Test
    fun `status chips are only the features that are on, and the battery is reported`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(FountainFixtures.ctw3, Household.feeder)
        val viewModel = viewModel(3003L)
        collect(viewModel)
        advanceUntilIdle()

        val page = viewModel.uiState.value.page!!
        // Do not disturb is on, the lock off; smart mode is on the bar, not a chip.
        assertEquals(listOf("ctw3_dnd"), page.modes.map { it.key })
        assertEquals("smart", page.modeBar?.selected)
        assertEquals(PartReading.Percent(9), page.battery?.reading)
        assertTrue(page.battery!!.attention)
        // The W5 has every feature off: no chip at all.
        devices.devices.value = listOf(FountainFixtures.w5, FountainFixtures.relay)
        val w5 = viewModel(3002L)
        collect(w5)
        advanceUntilIdle()
        assertTrue(w5.uiState.value.page!!.modes.isEmpty())
        assertNull(w5.uiState.value.page!!.battery)
    }

    @Test
    fun `a relayed fountain says whether its relay is online, and when it was seen`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.page?.relay?.online)
        assertEquals(Household.clock.instant(), state.now)
        assertEquals(SeenAgo.Hours(3), SeenAgo.of(state.page!!.device.lastSeen!!, state.now))

        // The relay drops off: the page follows it, offline.
        devices.devices.value =
            listOf(FountainFixtures.w7h, FountainFixtures.w5, FountainFixtures.relay.copy(online = false))
        runCurrent()
        assertEquals(false, viewModel.uiState.value.page?.relay?.online)
    }

    @Test
    fun `drinks without a duration are counted, and their durations said to be unknown`() = runTest(main.dispatcher) {
        timeline.days.value = mapOf(
            Household.today to FountainFixtures.untimedItems,
            Household.today.minusDays(1) to FountainFixtures.yesterdayItems.map { it.copy(durationSec = null) },
        )
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        val page = viewModel.uiState.value.page!!
        assertEquals(5, page.today.drinks)
        assertNull(page.today.averageDurationSec)
        assertFalse(page.durationsKnown)
        assertTrue(page.drinks.all { it.durationSec == null })
    }

    @Test
    fun `the pet filter is the page's own state, and an unreachable server is said`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onPetFilter(2)
        live.current.value = LiveUpdatesStatus.Waiting(DomainError.Network("down"), 1.seconds)
        runCurrent()
        assertEquals(2L, viewModel.uiState.value.petFilter)
        assertTrue(viewModel.uiState.value.offline)
    }

    @Test
    fun `a device the server no longer has shows as gone`() = runTest(main.dispatcher) {
        devices.devices.value = emptyList()
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loaded)
        assertNull(viewModel.uiState.value.page)
    }

    @Test
    fun `only a CTW3 offers the drink sync`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(FountainFixtures.w7h, FountainFixtures.w5, FountainFixtures.ctw3, FountainFixtures.onlineRelay)
        val w5 = viewModel(3002L)
        val ctw3 = viewModel(3003L)
        collect(w5)
        collect(ctw3)
        advanceUntilIdle()

        assertFalse(w5.uiState.value.syncsDrinks)
        assertTrue(ctw3.uiState.value.syncsDrinks)
        assertNull(ctw3.uiState.value.drinkSync.lastSync)
    }

    @Test
    fun `Sync drinks shows each step, uploads what is new, re-reads History and keeps the time`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(FountainFixtures.ctw3, FountainFixtures.onlineRelay)
        val gate = CompletableDeferred<Unit>().also { bluetooth.gate = it }
        val drink = DrinkRecord(Household.clock.instant().epochSecond - 3600 - 946_684_800L, 25)
        bluetooth.records = listOf(drink)
        val viewModel = viewModel(3003L)
        collect(viewModel)
        advanceUntilIdle()
        timeline.refreshedDays.clear()

        viewModel.onSyncDrinks()
        runCurrent()
        assertEquals(DrinkSyncStep.Connected, viewModel.uiState.value.drinkSync.step)
        assertTrue(viewModel.uiState.value.drinkSync.running)
        // A second press while it runs starts nothing.
        viewModel.onSyncDrinks()

        gate.complete(Unit)
        advanceUntilIdle()
        val state = viewModel.uiState.value.drinkSync
        assertEquals(DrinkSyncStep.Done(1), state.step)
        assertFalse(state.running)
        assertEquals(Household.clock.instant(), state.lastSync)
        assertEquals(listOf(listOf(drink)), drinkSync.uploads)
        assertEquals(listOf(FakeDrinkSyncRepository.MAC), bluetooth.macs)
        assertEquals(listOf(Household.today), timeline.refreshedDays)
    }

    @Test
    fun `without the Bluetooth permissions Android is asked, and a refusal is said`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(FountainFixtures.ctw3, FountainFixtures.onlineRelay)
        bluetoothGranted = false
        val viewModel = viewModel(3003L)
        collect(viewModel)
        val asked = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.requestBluetoothPermission.collect { asked += it } }
        advanceUntilIdle()

        viewModel.onSyncDrinks()
        runCurrent()
        assertEquals(1, asked.size)
        assertTrue(viewModel.uiState.value.drinkSync.askingPermission)
        assertTrue(bluetooth.macs.isEmpty())

        viewModel.onBluetoothPermissionResult(false)
        runCurrent()
        assertEquals(
            DrinkSyncStep.Failed(DrinkSyncFailure.Link(FountainLinkFailure.PermissionMissing)),
            viewModel.uiState.value.drinkSync.step,
        )
        assertFalse(viewModel.uiState.value.drinkSync.running)

        // Allowed on the next press: the sync starts at once.
        viewModel.onSyncDrinks()
        viewModel.onBluetoothPermissionResult(true)
        advanceUntilIdle()
        assertEquals(DrinkSyncStep.Done(0), viewModel.uiState.value.drinkSync.step)
    }

    @Test
    fun `Bluetooth off, a missing secret and a refusal by the fountain are each the card's reason`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(FountainFixtures.ctw3, FountainFixtures.onlineRelay)
        val viewModel = viewModel(3003L)
        collect(viewModel)
        advanceUntilIdle()

        bluetooth.availability = BluetoothAvailability.Off
        viewModel.onSyncDrinks()
        advanceUntilIdle()
        assertEquals(
            DrinkSyncStep.Failed(DrinkSyncFailure.Link(FountainLinkFailure.BluetoothOff)),
            viewModel.uiState.value.drinkSync.step,
        )

        bluetooth.availability = BluetoothAvailability.Ready
        drinkSync.bleSyncResult = Outcome.Failure(DomainError.Server(409, "secret_unknown", "Import from PetKit."))
        viewModel.onSyncDrinks()
        advanceUntilIdle()
        assertEquals(
            DrinkSyncStep.Failed(DrinkSyncFailure.SecretUnknown("Import from PetKit.")),
            viewModel.uiState.value.drinkSync.step,
        )

        drinkSync.bleSyncResult = Outcome.Success(FakeDrinkSyncRepository.info())
        bluetooth.failure = FountainLinkFailure.SecretRefused
        viewModel.onSyncDrinks()
        advanceUntilIdle()
        assertEquals(
            DrinkSyncStep.Failed(DrinkSyncFailure.Link(FountainLinkFailure.SecretRefused)),
            viewModel.uiState.value.drinkSync.step,
        )
        assertNull(viewModel.uiState.value.drinkSync.lastSync)
    }
}
