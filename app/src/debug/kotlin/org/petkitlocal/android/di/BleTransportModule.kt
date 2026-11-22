package org.petkitlocal.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.android.EndToEndBleTransport
import org.petkitlocal.core.data.drinksync.ble.BleTransport

/**
 * The phone's own Bluetooth, for a fountain's drink history (issue #104), through
 * [EndToEndBleTransport]: the radio itself unless an end-to-end test put a scripted fountain in
 * its place. A release build binds the radio directly.
 */
@Module
@InstallIn(SingletonComponent::class)
interface BleTransportModule {
    @Binds
    fun bleTransport(transport: EndToEndBleTransport): BleTransport
}
