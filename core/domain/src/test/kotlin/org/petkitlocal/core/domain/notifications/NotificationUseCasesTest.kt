package org.petkitlocal.core.domain.notifications

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.Session
import org.petkitlocal.core.domain.settings.NotificationKinds

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class NotificationUseCasesTest {
    private val paris = ZoneId.of("Europe/Paris")

    // 2026-11-15 23:30 UTC is already the 16th in Paris.
    private val lateEvening = Instant.parse("2026-11-15T23:30:00Z")

    private fun notification(kind: String, deviceId: Long? = 7, code: String? = null) =
        ServerNotification(1, replay = false, kind, deviceId, petId = null, code = code, time = lateEvening)

    @Test
    fun `an event opens History on the local day it happened`() {
        val expected = NotificationTarget.History(7, LocalDate.of(2026, 11, 16))
        listOf(NotificationKinds.TOILET, NotificationKinds.FEED_SERVED, NotificationKinds.FEED_FAILED).forEach {
            assertEquals(expected, NotificationTarget.of(notification(it, code = "10"), paris))
        }
        assertEquals(expected, NotificationTarget.of(notification(NotificationKinds.ERROR, code = "7"), paris))
    }

    @Test
    fun `a condition opens the device, and no device the start`() {
        assertEquals(
            NotificationTarget.Device(7),
            NotificationTarget.of(notification("food_low", code = "food1"), paris),
        )
        assertEquals(
            NotificationTarget.Device(7),
            NotificationTarget.of(notification("consumable", code = "boxFull"), paris),
        )
        assertEquals(NotificationTarget.Device(7), NotificationTarget.of(notification("offline"), paris))
        // A fault the device states (its own text, not sent) rather than one it reported as an event.
        assertEquals(
            NotificationTarget.Device(7),
            NotificationTarget.of(notification("error", code = "errorMsg"), paris),
        )
        assertEquals(NotificationTarget.Home, NotificationTarget.of(notification("toilet", deviceId = null), paris))
    }

    @Test
    fun `the service is wanted while signed in with notifications on, once the session was read`() = runTest {
        val auth = StubAuth()
        val enabled = MutableStateFlow(true)
        val preferences = StubPreferences(enabled)
        val wanted = mutableListOf<Boolean>()
        backgroundScope.launch { ObserveNotificationServiceWantedUseCase(auth, preferences)().collect { wanted += it } }

        runCurrent()
        assertEquals(emptyList<Boolean>(), wanted) // still reading the session
        auth.authState.value = AuthState.SignedIn("https://pets.example.com")
        runCurrent()
        enabled.value = false
        runCurrent()
        enabled.value = true
        runCurrent()
        auth.authState.value = AuthState.SignedOut(SessionEndReason.ClosedByServer)
        runCurrent()

        assertEquals(listOf(true, false, true, false), wanted)
    }

    @Test
    fun `wanted is answered from a single flow`() = runTest {
        val auth = StubAuth().apply { authState.value = AuthState.SignedOut(null) }
        val values = ObserveNotificationServiceWantedUseCase(auth, StubPreferences(MutableStateFlow(true)))()
            .take(1).toList()
        assertEquals(listOf(false), values)
    }

    private class StubPreferences(override val enabled: Flow<Boolean>) : NotificationPreferencesRepository {
        override suspend fun setEnabled(enabled: Boolean) = Unit

        override val asked: Flow<NotificationPrompts> = flowOf(NotificationPrompts())

        override suspend fun markPermissionAsked() = Unit

        override suspend fun markBatteryAsked() = Unit
    }

    private class StubAuth : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.Unknown)

        override suspend fun lastServerBaseUrl(): String? = null

        override suspend fun probe(server: ServerAddress): Outcome<ServerInfo> = error("not used")

        override suspend fun signIn(server: ServerAddress, password: String, deviceName: String?): Outcome<Session> =
            error("not used")

        override suspend fun signOut() = Unit

        override suspend fun serverInfo(): Outcome<ServerInfo> = error("not used")

        override suspend fun changePassword(current: String, new: String): Outcome<Int> = error("not used")

        override suspend fun validateSession() = Unit

        override suspend fun endSession(reason: SessionEndReason) = Unit
    }
}
