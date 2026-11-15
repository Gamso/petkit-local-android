package org.petkitlocal.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** The phone's clock and time zone: what "today" means to the use cases. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    // Not a singleton: the zone is read each time, so a change of time zone takes effect.
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
