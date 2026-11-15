package org.petkitlocal.feature.home

import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.testing.MainDispatcherExtension

class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    @Test
    fun `starts loading, then shows what is coming`() = runTest(main.dispatcher) {
        val viewModel = HomeViewModel()

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            assertEquals(HomeUiState.Placeholder("M1"), awaitItem())
        }
    }
}
