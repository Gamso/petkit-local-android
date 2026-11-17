package org.petkitlocal.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.petkitlocal.core.domain.notifications.NotificationAccess

/** What Android lets the notifications do, read again at each call. */
class AndroidNotificationAccess @Inject constructor(@param:ApplicationContext private val context: Context) :
    NotificationAccess {
    override fun permissionNeeded(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    override fun batteryOptimised(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface PushModule {
    @Binds
    fun notificationAccess(impl: AndroidNotificationAccess): NotificationAccess
}

/** Emits each time a network becomes the default, so a socket waiting to reconnect tries at once. */
internal object NetworkReturns {
    @Suppress("TooGenericExceptionCaught") // A callback Android refuses leaves the backoff alone.
    fun of(context: Context): Flow<Unit> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }
        val registered = try {
            connectivity?.registerDefaultNetworkCallback(callback)
            connectivity != null
        } catch (_: RuntimeException) {
            false
        }
        awaitClose { if (registered) connectivity?.unregisterNetworkCallback(callback) }
    }
}
