package org.petkitlocal.feature.device

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.testing.MainDispatcherExtension

class DeviceViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    @Test
    fun `starts loading, then shows what is coming`() = runTest(main.dispatcher) {
        val viewModel = DeviceViewModel(SavedStateHandle(mapOf(DeviceViewModel.DEVICE_ID_KEY to 4L)))

        viewModel.uiState.test {
            assertEquals(DeviceUiState.Loading, awaitItem())
            assertEquals(DeviceUiState.Placeholder("M2", deviceId = 4L), awaitItem())
        }
    }
}
