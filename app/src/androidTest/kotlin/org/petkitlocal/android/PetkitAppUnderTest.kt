package org.petkitlocal.android

import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.e2e.AppUnderTest

/**
 * This app, for `TestServerRule` (core:e2e), which signs it out before each test: through the
 * app's own sign-out ([org.petkitlocal.core.domain.auth.AuthRepository.signOut]), which forgets
 * the session, the pinned certificate and every cache of the session, whether or not the server
 * can be reached. Every test then starts on sign-in, whatever the test before it left (issue #111).
 *
 * The tests share the app's process, so this is what replaces a fresh install between them; what
 * outlives a sign-out for a user (the phone's own preferences) outlives it here too.
 *
 * Named in `src/androidTest/resources/META-INF/services/org.petkitlocal.core.e2e.AppUnderTest`.
 */
class PetkitAppUnderTest : AppUnderTest {
    override fun signOut() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val auth = EntryPointAccessors.fromApplication(app, EndToEndEntryPoint::class.java).authRepository()
        val state = try {
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    // A server that can no longer be reached, or whose certificate the pin refuses,
                    // fails the sign-out's logout quickly; the session is forgotten either way.
                    auth.signOut()
                    auth.authState.first { it is AuthState.SignedOut }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "The app could not be signed out within ${TIMEOUT_MS / MS_PER_S} s before the test " +
                    "(state: ${auth.authState.value}); its sign-in screen would not have come.",
                e,
            )
        }
        check(state is AuthState.SignedOut) { "The app is $state after signing out" }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val MS_PER_S = 1000
    }
}
