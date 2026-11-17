package org.petkitlocal.core.domain.auth

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.UnauthorizedReason

class ChangePasswordUseCaseTest {
    private class ScriptedRepository(var answer: Outcome<Int>) : AuthRepository {
        val changes = mutableListOf<Pair<String, String>>()
        override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedIn("https://pets.example.com"))

        override suspend fun lastServerBaseUrl(): String? = null

        override suspend fun probe(server: ServerAddress): Outcome<ServerInfo> = error("not used")

        override suspend fun signIn(server: ServerAddress, password: String, deviceName: String?): Outcome<Session> =
            error("not used")

        override suspend fun signOut() = Unit

        override suspend fun serverInfo(): Outcome<ServerInfo> = error("not used")

        override suspend fun changePassword(current: String, new: String): Outcome<Int> =
            answer.also { changes += current to new }

        override suspend fun validateSession() = Unit

        override suspend fun endSession(reason: SessionEndReason) = Unit
    }

    private suspend fun change(
        answer: Outcome<Int>,
        current: String = "demo",
        new: String = "a new password",
        repeated: String = new,
    ): Pair<ChangePasswordResult, ScriptedRepository> {
        val repository = ScriptedRepository(answer)
        return ChangePasswordUseCase(repository)(current, new, repeated) to repository
    }

    @Test
    fun `a valid form is sent once and says how many phones were signed out`() = runTest {
        val (result, repository) = change(Outcome.Success(3))

        assertEquals(ChangePasswordResult.Success(signedOutPhones = 3), result)
        assertEquals(listOf("demo" to "a new password"), repository.changes)
    }

    @Test
    fun `the form is checked top field first, and nothing is sent until it passes`() = runTest {
        val cases = listOf(
            Triple("", "short", "x") to ChangePasswordFailure.CurrentMissing,
            Triple("demo", "", "") to ChangePasswordFailure.NewTooShort,
            Triple("demo", "1234567", "1234567") to ChangePasswordFailure.NewTooShort,
            Triple("demo", "x".repeat(1025), "x".repeat(1025)) to ChangePasswordFailure.NewTooLong,
            Triple("demo", "12345678", "12345679") to ChangePasswordFailure.Mismatch,
        )
        for ((form, expected) in cases) {
            val (result, repository) = change(Outcome.Success(0), form.first, form.second, form.third)
            assertEquals(ChangePasswordResult.Failure(expected), result, form.toString())
            assertEquals(emptyList<Pair<String, String>>(), repository.changes)
        }
        assertEquals(null, ChangePasswordUseCase.checkForm("demo", "12345678", "12345678"))
        assertEquals(null, ChangePasswordUseCase.checkForm("demo", "x".repeat(1024), "x".repeat(1024)))
    }

    @Test
    fun `each refusal becomes the failure the form shows`() = runTest {
        val cases = listOf(
            DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, 2.seconds) to
                ChangePasswordFailure.WrongCurrent(2.seconds),
            DomainError.RateLimited(30.seconds) to ChangePasswordFailure.LockedOut(30.seconds),
            DomainError.Validation(null, "password_rejected", "too weak") to ChangePasswordFailure.Rejected("too weak"),
            DomainError.Network("timeout") to ChangePasswordFailure.Unreachable,
            DomainError.SessionEnded(SessionEndReason.InvalidToken) to ChangePasswordFailure.SessionEnded,
            DomainError.Server(500, "boom", "it broke") to ChangePasswordFailure.ServerError(500, "it broke"),
            DomainError.UnexpectedResponse("html") to ChangePasswordFailure.ServerError(null, null),
        )
        for ((error, expected) in cases) {
            assertEquals(ChangePasswordResult.Failure(expected), change(Outcome.Failure(error)).first, error.toString())
        }
    }
}
