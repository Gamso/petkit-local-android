package org.petkitlocal.feature.device.generic

import androidx.lifecycle.SavedStateHandle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RefreshDevicesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.testing.FakeDeviceCommandRepository
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.common.CommandResult
import org.petkitlocal.feature.device.common.CommandSubject
import org.petkitlocal.feature.device.common.DeviceCommandController

@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy
class GenericDeviceViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val devices =
        FakeDeviceRepository(listOf(DeviceFixtures.fountain, DeviceFixtures.purifier, DeviceFixtures.puraMax))
    private val live = FakeLiveUpdatesRepository()
    private val commands = FakeDeviceCommandRepository()

    private fun viewModel(id: Long = DeviceFixtures.fountain.id) = GenericDeviceViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to id)),
        ObserveDeviceUseCase(devices),
        ObserveLiveUpdatesUseCase(live),
        RefreshDevicesUseCase(devices),
        RunDeviceActionUseCase(commands),
        SetEntityValueUseCase(commands),
        Household.clock,
    )

    /** The screen collects the state, as the route does. */
    private fun TestScope.shown(viewModel: GenericDeviceViewModel) =
        backgroundScope.launch { viewModel.uiState.collect {} }

    @Test
    fun `the device comes from the cache, with its status and sections, and keeps the socket open`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            assertFalse(viewModel.uiState.value.loaded)

            shown(viewModel)

            with(viewModel.uiState.value) {
                assertTrue(loaded)
                assertEquals(DeviceFixtures.fountain, device)
                assertEquals(DeviceStatus.Idle, status)
                assertEquals(5, sections!!.settingsCount)
                assertEquals(listOf("rssi"), sections.diagnostics.map { it.key })
                assertEquals(Household.zone, zone)
            }
            assertEquals(1, live.collectors.value)
        }

    @Test
    fun `an accessory comes with the device relaying it, a WiFi device with none`() = runTest(main.dispatcher) {
        val accessory = viewModel(id = DeviceFixtures.purifier.id)
        shown(accessory)
        assertEquals(DeviceFixtures.puraMax, accessory.uiState.value.relay)

        val wifi = viewModel()
        shown(wifi)
        assertNull(wifi.uiState.value.relay)
    }

    @Test
    fun `a device the server no longer has is gone, not loading`() = runTest(main.dispatcher) {
        val viewModel = viewModel(id = 404)
        shown(viewModel)

        assertTrue(viewModel.uiState.value.loaded)
        assertNull(viewModel.uiState.value.device)
    }

    @Test
    fun `the cache moving moves the screen`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)

        devices.devices.value = listOf(DeviceFixtures.fountain.copy(online = false))

        assertEquals(DeviceStatus.Offline, viewModel.uiState.value.status)
    }

    @Test
    fun `an action is sent at once, and says how it was delivered until it times out`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        commands.result = Outcome.Success(CommandDelivery.Queued)

        viewModel.onAction(DeviceFixtures.flush)

        assertEquals(listOf(FakeDeviceCommandRepository.Action(3001, "fountain_flush", false)), commands.actions)
        val feedback = viewModel.uiState.value.commands.feedback!!
        assertEquals(CommandSubject.Action("fountain_flush", "Drain & Flush"), feedback.subject)
        assertEquals(CommandResult.Sent(CommandDelivery.Queued), feedback.result)

        advanceTimeBy(DeviceCommandController.SENT_SHOWN_MS + 1)
        assertNull(viewModel.uiState.value.commands.feedback)
    }

    @Test
    fun `a destructive action waits for the user's confirmation, then goes with confirm`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)

        viewModel.onAction(DeviceFixtures.drain)
        assertEquals(DeviceFixtures.drain, viewModel.uiState.value.commands.confirming)
        assertTrue(commands.actions.isEmpty())

        viewModel.onDismissConfirmation()
        assertNull(viewModel.uiState.value.commands.confirming)
        assertTrue(commands.actions.isEmpty())

        viewModel.onAction(DeviceFixtures.drain)
        viewModel.onConfirmAction()
        assertNull(viewModel.uiState.value.commands.confirming)
        assertEquals(listOf(FakeDeviceCommandRepository.Action(3001, "fountain_drain", true)), commands.actions)
    }

    @Test
    fun `while an action is in flight it says so, and a second tap sends nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        val gate = CompletableDeferred<Unit>()
        commands.gate = gate

        viewModel.onAction(DeviceFixtures.flush)
        viewModel.onAction(DeviceFixtures.flush)

        assertEquals(1, commands.actions.size)
        assertEquals(setOf("fountain_flush"), viewModel.uiState.value.commands.sending)
        assertEquals(CommandResult.Sending, viewModel.uiState.value.commands.feedback!!.result)
        gate.complete(Unit)
        assertEquals(emptySet<String>(), viewModel.uiState.value.commands.sending)
    }

    @Test
    fun `a refusal shows the server's error, and a tap dismisses it`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        val refusal = DomainError.Validation(null, "refused", "The fountain is busy")
        commands.result = Outcome.Failure(refusal)

        viewModel.onAction(DeviceFixtures.refill)

        assertEquals(CommandResult.Failed(refusal), viewModel.uiState.value.commands.feedback!!.result)
        viewModel.onDismissFeedback()
        assertNull(viewModel.uiState.value.commands.feedback)
    }

    @Test
    fun `the offline banner follows the socket and the last refresh`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        assertFalse(viewModel.uiState.value.offline)

        live.current.value = LiveUpdatesStatus.Waiting(DomainError.Network("down"), 2.seconds)
        assertTrue(viewModel.uiState.value.offline)

        live.current.value = LiveUpdatesStatus.Live
        devices.refreshResult = Outcome.Failure(DomainError.Network("down"))
        viewModel.onRefresh()
        assertTrue(viewModel.uiState.value.offline)
        assertEquals(1, devices.refreshCount)

        devices.refreshResult = Outcome.Success(Unit)
        viewModel.onRefresh()
        assertFalse(viewModel.uiState.value.offline)
        assertFalse(viewModel.uiState.value.refreshing)
    }
}
