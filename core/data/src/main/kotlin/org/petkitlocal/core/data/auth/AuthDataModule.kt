package org.petkitlocal.core.data.auth

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.PhoneNameProvider
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.auth.SessionTokenProvider

/** The scope session work runs in: it outlives every screen, as the session does. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {
    @Binds
    abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun serverAddressProvider(impl: AuthRepositoryImpl): ServerAddressProvider

    @Binds
    abstract fun sessionTokenProvider(impl: AuthRepositoryImpl): SessionTokenProvider

    @Binds
    abstract fun sessionEndListener(impl: AuthRepositoryImpl): SessionEndListener

    /**
     * Every cache kept for the session. Empty until a cache registers itself with
     * `@Binds @IntoSet abstract fun x(impl: XCache): SessionScopedCache`; signing out clears them all.
     */
    @Multibinds
    abstract fun sessionScopedCaches(): Set<SessionScopedCache>

    /** What is kept across sessions for one server; a sign-in to another one clears it. */
    @Multibinds
    abstract fun serverScopedCaches(): Set<ServerScopedCache>

    companion object {
        @Provides
        @Singleton
        @AuthCoroutineScope
        fun authCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        fun phoneNameProvider(): PhoneNameProvider =
            PhoneNameProvider { phoneModelName(Build.MANUFACTURER, Build.MODEL) }
    }
}

/** "Google" + "Pixel 8" gives "Google Pixel 8"; a model that already names its maker is kept as it is. */
internal fun phoneModelName(manufacturer: String?, model: String?): String {
    val maker = manufacturer.orEmpty().trim().replaceFirstChar { it.uppercaseChar() }
    val name = model.orEmpty().trim()
    return when {
        name.isEmpty() -> maker
        maker.isEmpty() || name.startsWith(maker, ignoreCase = true) -> name
        else -> "$maker $name"
    }
}
