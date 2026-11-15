package org.petkitlocal.core.domain.auth

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.UnauthorizedReason

class SignInUseCaseTest {
    private val epoch = Instant.fromEpochSeconds(0)
    private val session = Session("abc", "Pixel", epoch, epoch, epoch)
    private val info = ServerInfo(1, epoch, authenticated = false, serverVersion = null, session = null)

    private class ScriptedRepository(var probeResult: Outcome<ServerInfo>, var loginResult: Outcome<Session>) :
        AuthRepository {
        val probed = mutableListOf<ServerAddress>()
        val logins = mutableListOf<Triple<ServerAddress, String, String?>>()
        override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut(null))

        override suspend fun lastServerBaseUrl(): String? = null

        override suspend fun probe(server: ServerAddress) = probeResult.also { probed += server }

        override suspend fun signIn(server: ServerAddress, password: String, deviceName: String?) =
            loginResult.also { logins += Triple(server, password, deviceName) }

        override suspend fun signOut() = Unit

        override suspend fun validateSession() = Unit

        override suspend fun endSession(reason: SessionEndReason) = Unit
    }

    private fun repository(
        probe: Outcome<ServerInfo> = Outcome.Success(info),
        login: Outcome<Session> = Outcome.Success(session),
    ) = ScriptedRepository(probe, login)

    private suspend fun failureOf(repository: AuthRepository, address: String = "pets.example.com"): SignInFailure? =
        (SignInUseCase(repository)(address, "secret", "Pixel") as? SignInResult.Failure)?.failure

    @Test
    fun `normalises the address, probes it, then logs in with the phone's name`() = runTest {
        val repository = repository()

        val result = SignInUseCase(repository)(" Pets.Example.com/ ", "secret", "  Pixel 8  ")

        assertEquals(SignInResult.Success(session), result)
        assertEquals("https://pets.example.com", repository.probed.single().baseUrl)
        assertEquals(Triple(repository.probed.single(), "secret", "Pixel 8"), repository.logins.single())
    }

    @Test
    fun `a blank phone name is not sent, and a long one is cut to 64 characters`() = runTest {
        val repository = repository()
        SignInUseCase(repository)("pets.example.com", "secret", "   ")
        SignInUseCase(repository)("pets.example.com", "secret", "x".repeat(80))

        assertNull(repository.logins[0].third)
        assertEquals(64, repository.logins[1].third?.length)
    }

    @Test
    fun `a bad address stops before any request`() = runTest {
        val repository = repository()

        assertEquals(
            SignInFailure.InvalidAddress(AddressProblem.HttpsRequired),
            failureOf(repository, "http://example.com"),
        )
        assertTrue(repository.probed.isEmpty())
    }

    @Test
    fun `an empty password stops before any request, after the address`() = runTest {
        val repository = repository()

        assertEquals(
            SignInFailure.PasswordMissing,
            (SignInUseCase(repository)("pets.example.com", "", "Pixel") as SignInResult.Failure).failure,
        )
        assertEquals(SignInFailure.InvalidAddress(AddressProblem.Empty), SignInUseCase.checkForm("", ""))
        assertTrue(repository.probed.isEmpty())
    }

    @Test
    fun `a probe answered by something else is not a petkit-local server`() = runTest {
        val notPetkit = listOf(
            DomainError.NotFound("not_found", ""),
            DomainError.UnexpectedResponse("<html>"),
            DomainError.Unauthorized(UnauthorizedReason.Other),
        )
        notPetkit.forEach { error ->
            val repository = repository(probe = Outcome.Failure(error))
            assertEquals(SignInFailure.NotPetkitLocal, failureOf(repository), "$error")
            assertTrue(repository.logins.isEmpty())
        }
    }

    @Test
    fun `the probe tells a server without a password, an old API and an unreachable one`() = runTest {
        assertEquals(
            SignInFailure.PasswordNotSet,
            failureOf(repository(probe = Outcome.Failure(DomainError.Unauthorized(UnauthorizedReason.PasswordNotSet)))),
        )
        assertEquals(
            SignInFailure.IncompatibleServer(2),
            failureOf(repository(probe = Outcome.Success(info.copy(apiVersion = 2)))),
        )
        assertEquals(
            SignInFailure.Unreachable("timeout"),
            failureOf(repository(probe = Outcome.Failure(DomainError.Network("timeout")))),
        )
    }

    @Test
    fun `login refusals keep the server's wait`() = runTest {
        val wrong = DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, 2.seconds)
        assertEquals(SignInFailure.WrongPassword(2.seconds), failureOf(repository(login = Outcome.Failure(wrong))))
        assertEquals(
            SignInFailure.LockedOut(30.seconds),
            failureOf(repository(login = Outcome.Failure(DomainError.RateLimited(30.seconds)))),
        )
        assertEquals(
            SignInFailure.PasswordNotSet,
            failureOf(repository(login = Outcome.Failure(DomainError.Unauthorized(UnauthorizedReason.PasswordNotSet)))),
        )
        assertEquals(
            SignInFailure.ServerError(503, "down"),
            failureOf(repository(login = Outcome.Failure(DomainError.Server(503, null, "down")))),
        )
    }
}
