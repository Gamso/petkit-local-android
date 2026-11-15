package org.petkitlocal.feature.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.SignOutUseCase
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAuthRepository(AuthState.SignedIn("https://pets.example.com"))

    private fun TestScope.viewModel() =
        SettingsViewModel(SignOutUseCase(repository), ObserveAuthStateUseCase(repository))
            .also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    @Test
    fun `shows the server signed in to`() = runTest(main.dispatcher) {
        val state = viewModel().uiState.value

        assertEquals("https://pets.example.com", state.server)
    }

    @Test
    fun `signing out asks first, and a dismissal changes nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onSignOutClick()
        assertTrue(viewModel.uiState.value.confirmingSignOut)
        viewModel.onSignOutDismiss()

        assertFalse(viewModel.uiState.value.confirmingSignOut)
        assertEquals(0, repository.signOuts)
    }

    @Test
    fun `a confirmed sign-out ends the session once, however many taps`() = runTest(main.dispatcher) {
        val release = CompletableDeferred<Unit>()
        repository.gate = { release.await() }
        val viewModel = viewModel()

        viewModel.onSignOutClick()
        viewModel.onSignOutConfirm()
        runCurrent()
        assertTrue(viewModel.uiState.value.signingOut)
        viewModel.onSignOutConfirm()
        release.complete(Unit)
        runCurrent()

        assertEquals(1, repository.signOuts)
        assertFalse(viewModel.uiState.value.signingOut)
        assertEquals(AuthState.SignedOut(null), repository.authState.value)
        assertNull(viewModel.uiState.value.server)
    }
}
