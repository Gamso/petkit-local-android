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
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.GetServerInfoUseCase
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.SignOutUseCase
import org.petkitlocal.core.domain.settings.AppVersionProvider
import org.petkitlocal.core.domain.settings.GetAppLicenceUseCase
import org.petkitlocal.core.domain.settings.GetNotificationSettingsUseCase
import org.petkitlocal.core.domain.settings.NotificationKinds
import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.petkitlocal.core.domain.settings.OpenSourceLicence
import org.petkitlocal.core.domain.settings.OpenSourceLicencesRepository
import org.petkitlocal.core.domain.settings.SetNotificationKindUseCase
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.FakeNotificationSettingsRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val auth = FakeAuthRepository(AuthState.SignedIn("https://pets.example.com"))
    private val notifications = FakeNotificationSettingsRepository()
    private val licences = object : OpenSourceLicencesRepository {
        override suspend fun components() = emptyList<OpenSourceComponent>()

        override suspend fun appLicence() = MIT
    }

    private fun TestScope.viewModel() = SettingsViewModel(
        signOut = SignOutUseCase(auth),
        observeAuthState = ObserveAuthStateUseCase(auth),
        serverInfo = GetServerInfoUseCase(auth),
        notificationSettings = GetNotificationSettingsUseCase(notifications),
        setNotificationKind = SetNotificationKindUseCase(notifications),
        appVersion = AppVersionProvider { "0.1.0" },
        appLicence = GetAppLicenceUseCase(licences),
    ).also { viewModel ->
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `shows the server, its version, this phone's name and the app's version`() = runTest(main.dispatcher) {
        val state = viewModel().uiState.value

        assertEquals("https://pets.example.com", state.server)
        assertEquals(Loaded.Ready(ServerDetails("1.4.0", "Pixel 8")), state.details)
        assertEquals("0.1.0", state.appVersion)
        assertEquals(Loaded.Ready(FakeNotificationSettingsRepository.DEFAULT), state.notifications)
    }

    @Test
    fun `what could not be read is said, and a retry reads only that again`() = runTest(main.dispatcher) {
        auth.serverInfoResult = Outcome.Failure(DomainError.Network("down"))
        notifications.failLoad = DomainError.Network("down")
        val viewModel = viewModel()
        assertEquals(Loaded.Failed, viewModel.uiState.value.details)
        assertEquals(Loaded.Failed, viewModel.uiState.value.notifications)

        notifications.failLoad = null
        viewModel.onRetry()
        runCurrent()

        assertEquals(Loaded.Ready(FakeNotificationSettingsRepository.DEFAULT), viewModel.uiState.value.notifications)
        assertEquals(Loaded.Failed, viewModel.uiState.value.details)
        assertEquals(2, auth.serverInfoCalls)
        assertEquals(2, notifications.loads)
    }

    @Test
    fun `a kind flipped off is sent, and the switch shows the server's answer once it came`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            val release = CompletableDeferred<Unit>()
            notifications.gate = { release.await() }

            viewModel.onKindToggle(NotificationKinds.OFFLINE, on = false)
            runCurrent()
            assertEquals(NotificationKinds.OFFLINE, viewModel.uiState.value.savingKind)
            val before = (viewModel.uiState.value.notifications as Loaded.Ready).value
            assertTrue(before.receives(NotificationKinds.OFFLINE)) // no optimistic UI
            viewModel.onKindToggle(NotificationKinds.TOILET, on = false) // one write at a time
            release.complete(Unit)
            runCurrent()

            assertEquals(listOf(NotificationKinds.KNOWN - NotificationKinds.OFFLINE), notifications.writes)
            val after = (viewModel.uiState.value.notifications as Loaded.Ready).value
            assertFalse(after.receives(NotificationKinds.OFFLINE))
            assertNull(viewModel.uiState.value.savingKind)
        }

    @Test
    fun `a refused flip changes nothing and says so`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        notifications.failWrite = DomainError.Network("down")

        viewModel.onKindToggle(NotificationKinds.TOILET, on = false)
        runCurrent()

        assertTrue(viewModel.uiState.value.kindNotSaved)
        assertEquals(Loaded.Ready(FakeNotificationSettingsRepository.DEFAULT), viewModel.uiState.value.notifications)
    }

    @Test
    fun `the app's own licence is read, and opens in a dialog`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertEquals(MIT, viewModel.uiState.value.appLicence)

        viewModel.onAppLicenceClick()
        assertTrue(viewModel.uiState.value.showingAppLicence)
        viewModel.onAppLicenceDismiss()
        assertFalse(viewModel.uiState.value.showingAppLicence)
    }

    @Test
    fun `signing out asks first, and a dismissal changes nothing`() = runTest(main.dispatcher) {
        val viewModel = viewModel()

        viewModel.onSignOutClick()
        assertTrue(viewModel.uiState.value.confirmingSignOut)
        viewModel.onSignOutDismiss()

        assertFalse(viewModel.uiState.value.confirmingSignOut)
        assertEquals(0, auth.signOuts)
    }

    @Test
    fun `a confirmed sign-out ends the session once, however many taps`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        val release = CompletableDeferred<Unit>()
        auth.gate = { release.await() }

        viewModel.onSignOutClick()
        viewModel.onSignOutConfirm()
        runCurrent()
        assertTrue(viewModel.uiState.value.signingOut)
        viewModel.onSignOutConfirm()
        release.complete(Unit)
        runCurrent()

        assertEquals(1, auth.signOuts)
        assertFalse(viewModel.uiState.value.signingOut)
        assertEquals(AuthState.SignedOut(null), auth.authState.value)
        assertNull(viewModel.uiState.value.server)
    }

    private companion object {
        val MIT = OpenSourceLicence("MIT License", "MIT License text")
    }
}
