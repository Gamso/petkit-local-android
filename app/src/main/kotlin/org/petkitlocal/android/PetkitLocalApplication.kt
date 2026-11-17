package org.petkitlocal.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import org.petkitlocal.push.NotificationServiceController

/**
 * The Hilt root: `:app` is the only module that sees every implementation (ARCHITECTURE.md §2).
 * It is also Coil's image loader factory: images (pet photos, from M3 media) are fetched with the
 * app's one OkHttp client, so they carry the bearer header to the user's server and to no one else.
 * It starts the notification service whenever the phone is signed in with notifications on.
 */
@HiltAndroidApp
class PetkitLocalApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var okHttpClient: Lazy<OkHttpClient>

    @Inject
    lateinit var notificationService: Lazy<NotificationServiceController>

    override fun onCreate() {
        super.onCreate()
        notificationService.get().start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() })) }
        .build()
}
