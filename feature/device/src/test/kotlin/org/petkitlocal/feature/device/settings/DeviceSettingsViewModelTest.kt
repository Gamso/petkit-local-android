package org.petkitlocal.feature.device.settings

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.device.SettingCategory
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.testing.FakeDeviceCommandRepository
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.common.CommandResult
import org.petkitlocal.feature.device.common.CommandSubject

class DeviceSettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val devices = FakeDeviceRepository(listOf(DeviceFixtures.fountain))
    private val commands = FakeDeviceCommandRepository()
    private val viewModel by lazy {
        DeviceSettingsViewModel(
            SavedStateHandle(mapOf(DEVICE_ID_KEY to DeviceFixtures.fountain.id)),
            ObserveDeviceUseCase(devices),
            ObserveLiveUpdatesUseCase(FakeLiveUpdatesRepository()),
            RunDeviceActionUseCase(commands),
            SetEntityValueUseCase(commands),
            Household.clock,
        )
    }

    private fun TestScope.shown() = backgroundScope.launch { viewModel.uiState.collect {} }

    @Test
    fun `every settable entity, by category`() = runTest(main.dispatcher) {
        shown()

        assertEquals(
            listOf(
                SettingCategory.WaterFlow to listOf("fountain_time", "flow_mode"),
                SettingCategory.WaterCare to listOf("water_change_time"),
                SettingCategory.ComfortAndSafety to listOf("child_lock"),
                SettingCategory.DisplayAndQuiet to listOf("refill_disturb_period"),
            ),
            viewModel.uiState.value.groups.map { group -> group.category to group.entities.map { it.key } },
        )
    }

    @Test
    fun `a switch is written when flipped, and the result shown`() = runTest(main.dispatcher) {
        shown()
        commands.result = Outcome.Success(CommandDelivery.Local)

        viewModel.onToggle("child_lock", true)

        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(3001, "child_lock", EntityValue.Bool(true))),
            commands.writes,
        )
        val feedback = viewModel.uiState.value.commands.feedback!!
        assertEquals(CommandSubject.Setting("child_lock", "Child Lock"), feedback.subject)
        assertEquals(CommandResult.Sent(CommandDelivery.Local), feedback.result)
    }

    @Test
    fun `a value out of range is refused in the editor, before any request`() = runTest(main.dispatcher) {
        shown()
        viewModel.onEdit("fountain_time")

        viewModel.onSave("fountain_time", EntityValue.Number(90.0))

        with(viewModel.uiState.value.editor!!) {
            assertEquals("fountain_time", entity.key)
            assertEquals(EntityWriteProblem.AboveMax(60.0), problem)
        }
        assertTrue(commands.writes.isEmpty())
    }

    @Test
    fun `a value is saved, and the editor closes once the server took it`() = runTest(main.dispatcher) {
        shown()
        val gate = CompletableDeferred<Unit>()
        commands.gate = gate
        viewModel.onEdit("fountain_time")

        viewModel.onSave("fountain_time", EntityValue.Number(5.0))

        assertTrue(viewModel.uiState.value.editor!!.saving)
        // A second Save while the first is in flight sends nothing.
        viewModel.onSave("fountain_time", EntityValue.Number(6.0))
        gate.complete(Unit)
        assertNull(viewModel.uiState.value.editor)
        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(3001, "fountain_time", EntityValue.Number(5.0))),
            commands.writes,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback!!.result)
    }

    @Test
    fun `a refusal from the server stays in the editor, which can be tried again`() = runTest(main.dispatcher) {
        shown()
        val refusal = DomainError.Validation(null, "refused", "Flow Mode must be one of: ...")
        commands.result = Outcome.Failure(refusal)
        viewModel.onEdit("flow_mode")

        viewModel.onSave("flow_mode", EntityValue.Text("intermittent"))

        with(viewModel.uiState.value.editor!!) {
            assertEquals(refusal, error)
            assertEquals(false, saving)
        }
        // In place, not in the banner as well.
        assertNull(viewModel.uiState.value.commands.feedback)

        viewModel.onDismissEditor()
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun `the open editor follows the cache`() = runTest(main.dispatcher) {
        shown()
        viewModel.onEdit("flow_mode")

        val changed = DeviceFixtures.flowMode.copy(display = "intermittent")
        devices.devices.value = listOf(
            DeviceFixtures.fountain.copy(
                entities = DeviceFixtures.fountain.entities.map { if (it.key == "flow_mode") changed else it },
            ),
        )

        assertEquals(changed, viewModel.uiState.value.editor!!.entity)
    }
}
