package org.petkitlocal.feature.live

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.testing.MainDispatcherExtension

class LiveViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    @Test
    fun `starts loading, then shows what is coming`() = runTest(main.dispatcher) {
        val viewModel = LiveViewModel(SavedStateHandle(mapOf(LiveViewModel.DEVICE_ID_KEY to 4L)))

        viewModel.uiState.test {
            assertEquals(LiveUiState.Loading, awaitItem())
            assertEquals(LiveUiState.Placeholder("M4", deviceId = 4L), awaitItem())
        }
    }
}
