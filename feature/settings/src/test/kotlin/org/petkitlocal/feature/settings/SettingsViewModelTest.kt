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
import org.petkitlocal.core.domain.notifications.MarkNotificationPromptUseCase
import org.petkitlocal.core.domain.notifications.NotificationPrompts
import org.petkitlocal.core.domain.notifications.ObservePhoneNotificationsUseCase
import org.petkitlocal.core.domain.notifications.SetNotificationsEnabledUseCase
import org.petkitlocal.core.domain.settings.AppVersionProvider
import org.petkitlocal.core.domain.settings.GetAppLicenceUseCase
import org.petkitlocal.core.domain.settings.GetNotificationSettingsUseCase
import org.petkitlocal.core.domain.settings.NotificationKinds
import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.petkitlocal.core.domain.settings.OpenSourceLicence
import org.petkitlocal.core.domain.settings.OpenSourceLicencesRepository
import org.petkitlocal.core.domain.settings.SetNotificationKindUseCase
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.FakeNotificationAccess
import org.petkitlocal.core.testing.FakeNotificationPreferencesRepository
import org.petkitlocal.core.testing.FakeNotificationSettingsRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val auth = FakeAuthRepository(AuthState.SignedIn("https://pets.example.com"))
    private val notifications = FakeNotificationSettingsRepository()
    private val preferences = FakeNotificationPreferencesRepository()
    private val access = FakeNotificationAccess()
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
        observePhoneNotifications = ObservePhoneNotificationsUseCase(preferences, access),
        setNotificationsEnabled = SetNotificationsEnabledUseCase(preferences),
        markPrompt = MarkNotificationPromptUseCase(preferences),
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
    fun `this phone's notifications are on, with what Android allows`() = runTest(main.dispatcher) {
        access.batteryOptimised = true
        val phone = viewModel().uiState.value.phone!!

        assertTrue(phone.enabled)
        assertFalse(phone.permissionNeeded)
        assertTrue(phone.batteryOptimised)
    }

    @Test
    fun `Android's settings are read again when the screen comes back`() = runTest(main.dispatcher) {
        access.permissionNeeded = true
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.phone!!.permissionNeeded)

        access.permissionNeeded = false
        viewModel.onResume()
        runCurrent()

        assertFalse(viewModel.uiState.value.phone!!.permissionNeeded)
    }

    @Test
    fun `turned on, the permission is asked for, then the battery is explained once`() = runTest(main.dispatcher) {
        preferences.enabled.value = false
        access.permissionNeeded = true
        access.batteryOptimised = true
        val viewModel = viewModel()
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onNotificationsToggle(on = true)
        runCurrent()
        assertTrue(preferences.enabled.value)
        assertEquals(listOf(SettingsEffect.RequestNotificationPermission(askedBefore = false)), effects)

        access.permissionNeeded = false
        viewModel.onNotificationPermissionResult(granted = true)
        runCurrent()
        assertTrue(preferences.asked.value.permission)
        assertTrue(viewModel.uiState.value.explainingBattery)

        viewModel.onBatteryExplanationConfirm()
        runCurrent()
        assertFalse(viewModel.uiState.value.explainingBattery)
        assertEquals(SettingsEffect.RequestBatteryExemption, effects.last())
        assertTrue(preferences.asked.value.battery)

        // Asked once: turned off and on again, the battery is not explained again by itself.
        viewModel.onNotificationsToggle(on = false)
        viewModel.onNotificationsToggle(on = true)
        runCurrent()
        assertFalse(viewModel.uiState.value.explainingBattery)
    }

    @Test
    fun `not now is an answer too, and the button next to the switch still explains it`() = runTest(main.dispatcher) {
        access.batteryOptimised = true
        val viewModel = viewModel()

        viewModel.onNotificationsToggle(on = true)
        runCurrent()
        assertTrue(viewModel.uiState.value.explainingBattery)
        viewModel.onBatteryExplanationDismiss()
        runCurrent()
        assertEquals(NotificationPrompts(battery = true), preferences.asked.value)

        viewModel.onAllowBackgroundClick()
        assertTrue(viewModel.uiState.value.explainingBattery)
    }

    @Test
    fun `allowing notifications again says it was asked before`() = runTest(main.dispatcher) {
        preferences.asked.value = NotificationPrompts(permission = true)
        access.permissionNeeded = true
        val viewModel = viewModel()
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onAllowNotificationsClick()
        runCurrent()

        assertEquals(listOf(SettingsEffect.RequestNotificationPermission(askedBefore = true)), effects)
    }

    @Test
    fun `turned off, nothing is asked`() = runTest(main.dispatcher) {
        access.permissionNeeded = true
        access.batteryOptimised = true
        val viewModel = viewModel()
        val effects = mutableListOf<SettingsEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onNotificationsToggle(on = false)
        runCurrent()

        assertFalse(preferences.enabled.value)
        assertTrue(effects.isEmpty())
        assertFalse(viewModel.uiState.value.explainingBattery)
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
