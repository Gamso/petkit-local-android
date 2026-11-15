package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.petkitlocal.core.data.QuickActionsRepositoryImpl
import org.petkitlocal.core.data.QuickActionsServerCache
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.repository.QuickActionsRepository

/** The user's actions in view per device, kept for one server across its sessions. */
@Module
@InstallIn(SingletonComponent::class)
interface QuickActionsDataModule {
    @Binds
    fun quickActionsRepository(impl: QuickActionsRepositoryImpl): QuickActionsRepository

    @Binds
    @IntoSet
    fun quickActionsServerCache(impl: QuickActionsServerCache): ServerScopedCache
}
