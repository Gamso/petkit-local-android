package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.data.talk.AudioRecordMicrophone
import org.petkitlocal.core.data.talk.Microphone
import org.petkitlocal.core.data.talk.TalkRepositoryImpl
import org.petkitlocal.core.domain.talk.TalkRepository

/** Talking through a camera: the talk socket fed by the phone's microphone. */
@Module
@InstallIn(SingletonComponent::class)
interface TalkDataModule {
    @Binds
    fun talkRepository(impl: TalkRepositoryImpl): TalkRepository

    @Binds
    fun microphone(impl: AudioRecordMicrophone): Microphone
}
