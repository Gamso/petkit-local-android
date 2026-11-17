package org.petkitlocal.android

import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.ValidateSessionUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.notifications.NotificationTarget
import org.petkitlocal.core.testing.FakeAuthRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class AppViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeAuthRepository(AuthState.SignedIn("https://pets.example.com"))
    private val live = FakeLiveUpdatesRepository()
    private val viewModel = AppViewModel(
        ObserveAuthStateUseCase(repository),
        ValidateSessionUseCase(repository),
        ObserveLiveUpdatesUseCase(live),
    )

    @Test
    fun `the navigation follows the session, both ways`() = runTest(main.dispatcher) {
        assertEquals(AuthState.SignedIn("https://pets.example.com"), viewModel.authState.value)

        repository.endSession(SessionEndReason.InvalidToken)

        assertEquals(AuthState.SignedOut(SessionEndReason.InvalidToken), viewModel.authState.value)
    }

    @Test
    fun `the live updates are open only while someone collects them`() = runTest(main.dispatcher) {
        assertEquals(0, live.collectors.value)

        val job = launch { viewModel.liveUpdates.collect {} }
        runCurrent()
        assertEquals(1, live.collectors.value)

        job.cancel()
        runCurrent()
        assertEquals(0, live.collectors.value)
    }

    @Test
    fun `a tapped notification's page waits for the navigation, which opens it once`() {
        val target = NotificationTarget.History(1002, LocalDate.of(2026, 11, 16))

        viewModel.onNotificationOpened(target)
        assertEquals(target, viewModel.openTarget.value)

        viewModel.onTargetHandled()
        assertEquals(null, viewModel.openTarget.value)
    }

    @Test
    fun `coming to the foreground checks the session`() = runTest(main.dispatcher) {
        viewModel.onForeground()
        viewModel.onForeground()

        assertEquals(2, repository.validations)
    }
}
