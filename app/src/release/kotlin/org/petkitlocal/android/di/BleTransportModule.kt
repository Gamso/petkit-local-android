package org.petkitlocal.android.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.data.drinksync.ble.AndroidBleTransport
import org.petkitlocal.core.data.drinksync.ble.BleTransport

/** The phone's own Bluetooth, for a fountain's drink history (issue #104). */
@Module
@InstallIn(SingletonComponent::class)
interface BleTransportModule {
    @Binds
    fun bleTransport(radio: AndroidBleTransport): BleTransport
}
