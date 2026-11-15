package org.petkitlocal.feature.device

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension

class DeviceRouteViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val devices = FakeDeviceRepository(Household.devices)

    private fun viewModel(id: Long) =
        DeviceRouteViewModel(SavedStateHandle(mapOf(DEVICE_ID_KEY to id)), ObserveDeviceUseCase(devices))

    @Test
    fun `the dispatch knows the family once the cache is read, and follows a removal`() = runTest(main.dispatcher) {
        viewModel(Household.litterBox.id).kind.test {
            assertEquals(DeviceKindState.Loading, awaitItem())
            assertEquals(DeviceKindState.Known(DeviceFamily.LitterBox), awaitItem())

            // A live update of the same device is not a new dispatch.
            devices.devices.value =
                Household.devices.map { if (it.id == Household.litterBox.id) it.copy(online = false) else it }
            devices.devices.value = Household.devices.filter { it.id != Household.litterBox.id }
            assertEquals(DeviceKindState.Gone, awaitItem())
        }
    }

    @Test
    fun `an unknown device is gone`() = runTest(main.dispatcher) {
        viewModel(404).kind.test {
            assertEquals(DeviceKindState.Loading, awaitItem())
            assertEquals(DeviceKindState.Gone, awaitItem())
        }
    }
}
