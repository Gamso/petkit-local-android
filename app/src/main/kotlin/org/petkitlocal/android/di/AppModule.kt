package org.petkitlocal.android.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.domain.auth.LocalNetworkAccess
import org.petkitlocal.core.domain.settings.AppVersionProvider
import org.petkitlocal.core.domain.talk.MicrophoneAccess

/** What only the app module can bind: the Android permissions the domain asks about, and its version. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Android 17 (API 37) brought `ACCESS_LOCAL_NETWORK`. */
    private const val LOCAL_NETWORK_PERMISSION_SDK = 37

    @Provides
    fun localNetworkAccess(@ApplicationContext context: Context): LocalNetworkAccess = LocalNetworkAccess {
        Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
            PackageManager.PERMISSION_GRANTED
    }

    @Provides
    fun microphoneAccess(@ApplicationContext context: Context): MicrophoneAccess = MicrophoneAccess {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** The installed package's `versionName` (app/build.gradle.kts), for About. */
    @Provides
    fun appVersion(@ApplicationContext context: Context): AppVersionProvider = AppVersionProvider {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
