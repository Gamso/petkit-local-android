package org.petkitlocal.feature.auth

import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.testing.MainDispatcherExtension

class SignInViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    @Test
    fun `starts loading, then shows what is coming`() = runTest(main.dispatcher) {
        val viewModel = SignInViewModel()

        viewModel.uiState.test {
            assertEquals(SignInUiState.Loading, awaitItem())
            assertEquals(SignInUiState.Placeholder("M1"), awaitItem())
        }
    }
}
