package org.petkitlocal.feature.auth

import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.UnauthorizedReason
import org.petkitlocal.core.domain.auth.AddressProblem
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.GetSignInDefaultsUseCase
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.PhoneNameProvider
import org.petkitlocal.core.domain.auth.SignInFailure
import org.petkitlocal.core.domain.auth.SignInUseCase
import org.petkitlocal.core.domain.auth.TrustCertificateUseCase
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.FakeServerTrustRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy, runCurrent
class SignInViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAuthRepository().apply { lastServer = "https://pets.example.com" }
    private val trust = FakeServerTrustRepository()
    private var localNetworkPermissionNeeded = false

    /** A view model whose state is collected, as the screen does, for the length of the test. */
    private fun TestScope.viewModel(): SignInViewModel = SignInViewModel(
        signIn = SignInUseCase(repository),
        defaults = GetSignInDefaultsUseCase(repository, PhoneNameProvider { "Google Pixel 8" }),
        observeAuthState = ObserveAuthStateUseCase(repository),
        localNetwork = { localNetworkPermissionNeeded },
        trustCertificate = TrustCertificateUseCase(trust),
    ).also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    private val SignInViewModel.state get() = uiState.value

    private fun SignInViewModel.fill(password: String = "demo") {
        onPasswordChange(password)
    }

    @Test
    fun `the form starts empty, then from the last server and the phone's model`() = runTest(main.dispatcher) {
        val viewModel = SignInViewModel(
            signIn = SignInUseCase(repository),
            defaults = GetSignInDefaultsUseCase(repository, PhoneNameProvider { "Google Pixel 8" }),
            observeAuthState = ObserveAuthStateUseCase(repository),
            localNetwork = { localNetworkPermissionNeeded },
            trustCertificate = TrustCertificateUseCase(trust),
        )

        viewModel.uiState.test {
            val filled = awaitItem()
            assertEquals("https://pets.example.com", filled.address)
            assertEquals("Google Pixel 8", filled.phoneName)
            assertTrue(filled.canSubmit) // pressing it with no password says so, under the field
        }
    }

    @Test
    fun `signing in sends the normalised address, the password and the name, then forgets the password`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            viewModel.onAddressChange("Pets.Example.com/")
            viewModel.fill()
            viewModel.onPhoneNameChange("Kitchen phone")
            assertTrue(viewModel.state.canSubmit)

            viewModel.onSubmit()
            runCurrent()

            assertEquals("", viewModel.state.password)
            assertFalse(viewModel.state.submitting)
            assertNull(viewModel.state.failure)
            val login = repository.logins.single()
            assertEquals("https://pets.example.com", login.server.baseUrl)
            assertEquals("demo", login.password)
            assertEquals("Kitchen phone", login.deviceName)
            assertEquals(AuthState.SignedIn("https://pets.example.com"), repository.authState.value)
        }

    @Test
    fun `an empty field is said under it and focused, before any request`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        val effects = mutableListOf<SignInEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onSubmit()
        runCurrent()
        assertEquals(SignInFailure.PasswordMissing, viewModel.state.failure)

        viewModel.onAddressChange("")
        viewModel.fill()
        viewModel.onSubmit()
        runCurrent()
        assertEquals(SignInFailure.InvalidAddress(AddressProblem.Empty), viewModel.state.failure)

        assertEquals(
            listOf(SignInEffect.Focus(SignInField.Password), SignInEffect.Focus(SignInField.Address)),
            effects,
        )
        assertTrue(repository.probes.isEmpty())
        viewModel.onAddressChange("pets.example.com")
        assertNull(viewModel.state.failure) // editing clears it
    }

    @Test
    fun `a wrong password puts the cursor back in the password`() = runTest(main.dispatcher) {
        repository.failLogin(DomainError.Unauthorized(UnauthorizedReason.InvalidPassword))
        val viewModel = viewModel()
        val effects = mutableListOf<SignInEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        viewModel.fill("wrong")

        viewModel.onSubmit()
        runCurrent()

        assertEquals(listOf(SignInEffect.Focus(SignInField.Password)), effects)
    }

    @Test
    fun `a refused certificate is shown, and trusting it pins it for the typed address and signs in`() =
        runTest(main.dispatcher) {
            repository.failProbe(DomainError.Certificate(FakeServerTrustRepository.UNTRUSTED))
            val viewModel = viewModel()
            viewModel.onAddressChange("https://petkit.home.lan")
            viewModel.fill()
            viewModel.onSubmit()
            runCurrent()
            assertEquals(SignInFailure.Certificate(FakeServerTrustRepository.UNTRUSTED), viewModel.state.failure)
            assertEquals("demo", viewModel.state.password) // kept for the retry

            repository.probeResult = Outcome.Success(FakeAuthRepository.INFO)
            viewModel.onTrustCertificate()
            runCurrent()

            val (server, certificate) = trust.trusted.single()
            assertEquals("https://petkit.home.lan", server?.baseUrl)
            assertEquals(FakeServerTrustRepository.CERTIFICATE, certificate)
            assertEquals(2, repository.probes.size)
            assertEquals("demo", repository.logins.single().password)
            assertNull(viewModel.state.failure)
            assertEquals(AuthState.SignedIn("https://petkit.home.lan"), repository.authState.value)
        }

    @Test
    fun `a certificate for another host offers nothing to trust`() = runTest(main.dispatcher) {
        repository.failProbe(DomainError.Certificate(FakeServerTrustRepository.WRONG_HOST))
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.onSubmit()
        runCurrent()

        viewModel.onTrustCertificate()
        runCurrent()

        assertTrue(trust.trusted.isEmpty())
        assertEquals(1, repository.probes.size)
        assertEquals(SignInFailure.Certificate(FakeServerTrustRepository.WRONG_HOST), viewModel.state.failure)
    }

    @Test
    fun `a certificate the repository will not pin leaves the form as it was`() = runTest(main.dispatcher) {
        repository.failProbe(DomainError.Certificate(FakeServerTrustRepository.CHANGED))
        trust.trustResult = false
        val viewModel = viewModel()
        viewModel.fill()
        viewModel.onSubmit()
        runCurrent()

        viewModel.onTrustCertificate()
        runCurrent()

        assertFalse(viewModel.state.submitting)
        assertEquals(1, repository.probes.size)
        assertEquals(SignInFailure.Certificate(FakeServerTrustRepository.CHANGED), viewModel.state.failure)
    }

    @Test
    fun `the phone's name opens and folds`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertFalse(viewModel.state.phoneNameExpanded)
        viewModel.onTogglePhoneName()
        assertTrue(viewModel.state.phoneNameExpanded)
        viewModel.onTogglePhoneName()
        assertFalse(viewModel.state.phoneNameExpanded)
    }

    @Test
    fun `while the request runs, the form says so and cannot be sent twice`() = runTest(main.dispatcher) {
        val release = CompletableDeferred<Unit>()
        repository.gate = { release.await() }
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.onSubmit()
        runCurrent()
        assertTrue(viewModel.state.submitting)
        assertFalse(viewModel.state.canSubmit)
        viewModel.onSubmit()
        release.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.submitting)
        assertEquals(1, repository.probes.size)
    }

    @Test
    fun `an address problem is shown on the address and goes once it is edited`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onAddressChange("http://pets.example.com")
        viewModel.fill()

        viewModel.onSubmit()
        runCurrent()
        assertEquals(SignInFailure.InvalidAddress(AddressProblem.HttpsRequired), viewModel.state.failure)
        assertEquals("demo", viewModel.state.password) // kept: only the address was wrong

        viewModel.onAddressChange("https://pets.example.com")
        assertNull(viewModel.state.failure)
        assertTrue(repository.probes.isEmpty())
    }

    @Test
    fun `plain HTTP on the LAN is accepted with a warning`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onAddressChange("192.168.1.20:8097")
        assertTrue(viewModel.state.plainHttpWarning)
        viewModel.onAddressChange("https://192.168.1.20")
        assertFalse(viewModel.state.plainHttpWarning)
    }

    @Test
    fun `a wrong password locks the button for the server's wait, counting down`() = runTest(main.dispatcher) {
        repository.failLogin(DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, retryAfter = 2.seconds))
        val viewModel = viewModel()
        viewModel.fill("wrong")

        viewModel.onSubmit()
        runCurrent()
        assertEquals(SignInFailure.WrongPassword(2.seconds), viewModel.state.failure)
        assertEquals("", viewModel.state.password) // a wrong password is typed again
        assertEquals(2, viewModel.state.lockedSeconds)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, viewModel.state.lockedSeconds)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, viewModel.state.lockedSeconds)
        assertEquals(SignInFailure.WrongPassword(2.seconds), viewModel.state.failure) // still said
    }

    @Test
    fun `a lockout counts down from Retry-After and its message goes when it ends`() = runTest(main.dispatcher) {
        repository.failLogin(DomainError.RateLimited(retryAfter = 3.seconds))
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.onSubmit()
        runCurrent()
        assertEquals(SignInFailure.LockedOut(3.seconds), viewModel.state.failure)
        assertEquals(3, viewModel.state.lockedSeconds)
        assertFalse(viewModel.state.canSubmit)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(0, viewModel.state.lockedSeconds)
        assertNull(viewModel.state.failure)
        assertTrue(viewModel.state.canSubmit) // the password was kept for the retry
    }

    @Test
    fun `server-side failures are kept for the screen to explain`() = runTest(main.dispatcher) {
        val cases = listOf(
            DomainError.Unauthorized(UnauthorizedReason.PasswordNotSet) to SignInFailure.PasswordNotSet,
            DomainError.UnexpectedResponse("<html>") to SignInFailure.NotPetkitLocal,
            DomainError.Network("timeout") to SignInFailure.Unreachable("timeout"),
        )
        cases.forEach { (error, expected) ->
            repository.failProbe(error)
            val viewModel = viewModel()
            viewModel.fill()
            viewModel.onSubmit()
            runCurrent()
            assertEquals(expected, viewModel.state.failure)
        }
    }

    @Test
    fun `the reason a session ended is shown until the next sign-in`() = runTest(main.dispatcher) {
        repository.authState.value = AuthState.SignedOut(SessionEndReason.InvalidToken)
        val viewModel = viewModel()
        assertEquals(SessionEndReason.InvalidToken, viewModel.state.sessionEnded)

        viewModel.fill()
        viewModel.onSubmit()
        runCurrent()
        assertNull(viewModel.state.sessionEnded)
    }

    @Test
    fun `a LAN server on Android 17 asks for local network access, with a reason, before any request`() =
        runTest(main.dispatcher) {
            localNetworkPermissionNeeded = true
            val viewModel = viewModel()
            val effects = mutableListOf<SignInEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects += it } }
            viewModel.onAddressChange("http://10.0.2.2:8097")
            viewModel.fill()

            viewModel.onSubmit()
            assertTrue(viewModel.state.localNetworkRationale)
            assertTrue(repository.probes.isEmpty())

            viewModel.onAllowLocalNetworkClick()
            runCurrent()
            assertEquals(listOf(SignInEffect.RequestLocalNetworkPermission), effects)

            localNetworkPermissionNeeded = false
            viewModel.onLocalNetworkResult(granted = true)
            runCurrent()
            assertFalse(viewModel.state.localNetworkRationale)
            assertEquals("http://10.0.2.2:8097", repository.probes.single().baseUrl)
        }

    @Test
    fun `a refused local network access is its own failure, not an unreachable server`() = runTest(main.dispatcher) {
        localNetworkPermissionNeeded = true
        val viewModel = viewModel()
        viewModel.onAddressChange("192.168.1.20:8097")
        viewModel.fill()

        viewModel.onSubmit()
        viewModel.onLocalNetworkResult(granted = false)

        assertEquals(SignInFailure.LocalNetworkDenied, viewModel.state.failure)
        assertFalse(viewModel.state.localNetworkRationale)
        assertTrue(repository.probes.isEmpty())
    }

    @Test
    fun `a server on the internet needs no local network access`() = runTest(main.dispatcher) {
        localNetworkPermissionNeeded = true
        val viewModel = viewModel()
        viewModel.fill()

        viewModel.onSubmit()
        runCurrent()

        assertFalse(viewModel.state.localNetworkRationale)
        assertEquals(1, repository.probes.size)
    }

    @Test
    fun `the password can be shown and hidden`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        viewModel.onTogglePasswordVisibility()
        assertTrue(viewModel.state.passwordVisible)
        viewModel.onTogglePasswordVisibility()
        assertFalse(viewModel.state.passwordVisible)
    }
}
