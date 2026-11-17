package org.petkitlocal.feature.auth.password

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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
import org.petkitlocal.core.domain.UnauthorizedReason
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ChangePasswordFailure
import org.petkitlocal.core.domain.auth.ChangePasswordUseCase
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy, runCurrent
class ChangePasswordViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAuthRepository(AuthState.SignedIn("https://pets.example.com"))
    private val viewModel = ChangePasswordViewModel(ChangePasswordUseCase(repository))
    private val state get() = viewModel.uiState.value

    private fun fill(current: String = "demo", new: String = "a new password", repeated: String = new) {
        viewModel.onCurrentChange(current)
        viewModel.onNewChange(new)
        viewModel.onRepeatedChange(repeated)
    }

    @Test
    fun `an incomplete form says what is missing and sends nothing`() = runTest(main.dispatcher) {
        viewModel.onSubmit()
        assertEquals(ChangePasswordFailure.CurrentMissing, state.failure)
        assertTrue(state.canSubmit) // never greyed out for a missing field

        fill(new = "a new password", repeated = "another one")
        viewModel.onSubmit()

        assertEquals(ChangePasswordFailure.Mismatch, state.failure)
        assertFalse(state.confirming)
        assertEquals(emptyList<Pair<String, String>>(), repository.passwordChanges)
    }

    @Test
    fun `editing a field clears only that field's message`() = runTest(main.dispatcher) {
        fill(new = "short")
        viewModel.onSubmit()
        assertEquals(ChangePasswordFailure.NewTooShort, state.failure)

        viewModel.onCurrentChange("demo2")
        assertEquals(ChangePasswordFailure.NewTooShort, state.failure)
        viewModel.onNewChange("long enough now")
        assertNull(state.failure)
    }

    @Test
    fun `a valid form is confirmed first, then sent once, and the fields are emptied`() = runTest(main.dispatcher) {
        repository.changePasswordResult = Outcome.Success(2)
        val release = CompletableDeferred<Unit>()
        repository.gate = { release.await() }
        fill()

        viewModel.onSubmit()
        assertTrue(state.confirming)
        assertEquals(emptyList<Pair<String, String>>(), repository.passwordChanges)

        viewModel.onConfirm()
        runCurrent()
        assertTrue(state.submitting)
        assertFalse(state.canSubmit)
        viewModel.onConfirm()
        release.complete(Unit)
        runCurrent()

        assertEquals(listOf("demo" to "a new password"), repository.passwordChanges)
        assertEquals(ChangePasswordUiState(signedOutPhones = 2), state)
    }

    @Test
    fun `a dismissed confirmation sends nothing`() = runTest(main.dispatcher) {
        fill()
        viewModel.onSubmit()
        viewModel.onDismissConfirm()
        viewModel.onConfirm()
        runCurrent()

        assertFalse(state.confirming)
        assertEquals(emptyList<Pair<String, String>>(), repository.passwordChanges)
    }

    @Test
    fun `a wrong current password empties it and waits out the server's lockout`() = runTest(main.dispatcher) {
        repository.changePasswordResult =
            Outcome.Failure(DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, 2.seconds))
        fill(current = "nope")
        viewModel.onSubmit()
        viewModel.onConfirm()
        runCurrent()

        assertEquals(ChangePasswordFailure.WrongCurrent(2.seconds), state.failure)
        assertEquals("", state.current)
        assertEquals("a new password", state.new)
        assertEquals(2, state.lockedSeconds)
        assertFalse(state.canSubmit) // the server says to wait

        advanceTimeBy(2_001)
        assertEquals(0, state.lockedSeconds)
        assertEquals(ChangePasswordFailure.WrongCurrent(2.seconds), state.failure) // said until edited
        viewModel.onCurrentChange("demo")
        assertNull(state.failure)
    }

    @Test
    fun `an unreachable server keeps everything typed for the retry`() = runTest(main.dispatcher) {
        repository.changePasswordResult = Outcome.Failure(DomainError.Network("timeout"))
        fill()
        viewModel.onSubmit()
        viewModel.onConfirm()
        runCurrent()

        assertEquals(ChangePasswordFailure.Unreachable, state.failure)
        assertEquals("demo", state.current)
        assertTrue(state.canSubmit)
    }
}
