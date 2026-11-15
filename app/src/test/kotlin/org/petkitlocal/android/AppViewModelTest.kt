package org.petkitlocal.android

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.ValidateSessionUseCase
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

class AppViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAuthRepository(AuthState.SignedIn("https://pets.example.com"))
    private val viewModel =
        AppViewModel(ObserveAuthStateUseCase(repository), ValidateSessionUseCase(repository))

    @Test
    fun `the navigation follows the session, both ways`() = runTest(main.dispatcher) {
        assertEquals(AuthState.SignedIn("https://pets.example.com"), viewModel.authState.value)

        repository.endSession(SessionEndReason.InvalidToken)

        assertEquals(AuthState.SignedOut(SessionEndReason.InvalidToken), viewModel.authState.value)
    }

    @Test
    fun `coming to the foreground checks the session`() = runTest(main.dispatcher) {
        viewModel.onForeground()
        viewModel.onForeground()

        assertEquals(2, repository.validations)
    }
}
