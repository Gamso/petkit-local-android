package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.petkitlocal.core.data.drinksync.DrinkSyncRepositoryImpl
import org.petkitlocal.core.data.drinksync.DrinkSyncServerCache
import org.petkitlocal.core.data.drinksync.ble.BleFountainBluetooth
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.drinksync.DrinkSyncRepository
import org.petkitlocal.core.domain.drinksync.FountainBluetooth

/**
 * A fountain's drink history over the phone's Bluetooth (issue #104). The [BleTransport] under it
 * is bound in `:app` (the radio in a release build; in a debug build the radio too, unless an
 * end-to-end test puts a scripted fountain in its place).
 *
 * [BleTransport]: org.petkitlocal.core.data.drinksync.ble.BleTransport
 */
@Module
@InstallIn(SingletonComponent::class)
interface DrinkSyncDataModule {
    @Binds
    fun drinkSyncRepository(impl: DrinkSyncRepositoryImpl): DrinkSyncRepository

    @Binds
    fun fountainBluetooth(impl: BleFountainBluetooth): FountainBluetooth

    @Binds
    @IntoSet
    fun drinkSyncServerCache(impl: DrinkSyncServerCache): ServerScopedCache
}
