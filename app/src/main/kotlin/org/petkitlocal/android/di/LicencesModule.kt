package org.petkitlocal.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.android.licences.AboutLibrariesRepository
import org.petkitlocal.core.domain.settings.OpenSourceLicencesRepository

/** The licences list is generated into the app module's resources, so the app binds it. */
@Module
@InstallIn(SingletonComponent::class)
interface LicencesModule {
    @Binds
    fun openSourceLicencesRepository(impl: AboutLibrariesRepository): OpenSourceLicencesRepository
}
