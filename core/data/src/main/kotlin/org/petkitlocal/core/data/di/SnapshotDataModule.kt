package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.data.video.MediaStoreSnapshotRepository
import org.petkitlocal.core.domain.repository.SnapshotRepository

/** The live screen's snapshots, saved among the phone's pictures. */
@Module
@InstallIn(SingletonComponent::class)
interface SnapshotDataModule {
    @Binds
    fun snapshotRepository(impl: MediaStoreSnapshotRepository): SnapshotRepository
}
