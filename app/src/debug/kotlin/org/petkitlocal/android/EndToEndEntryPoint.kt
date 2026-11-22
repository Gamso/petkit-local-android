package org.petkitlocal.android

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.domain.auth.AuthRepository

/**
 * What the end-to-end tests (src/androidTest) reach in the running app, in debug builds only:
 * the session, which they sign out before each test (`PetkitAppUnderTest`, issue #111). Hilt
 * builds the app's component from the app's own sources, so an entry point declared in the test
 * APK would not be part of it; this one is, and the release build has none.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface EndToEndEntryPoint {
    fun authRepository(): AuthRepository
}
