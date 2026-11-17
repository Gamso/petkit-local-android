package org.petkitlocal.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.petkitlocal.android.navigation.PetkitApp
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.push.NotificationLinks
import org.petkitlocal.push.NotificationServiceController

/**
 * The one activity. Its language is the phone's, or the one picked for the app in Android 13+'s
 * own settings: Android applies either, nothing here does.
 *
 * A tapped notification opens it with a link to a device or a History entry (NotificationLinks):
 * at start, or, as it is singleTop, delivered to the running one.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var notificationService: NotificationServiceController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Once: a recreated activity has handled its intent already.
        if (savedInstanceState == null) open(intent)
        setContent {
            PkTheme {
                PetkitApp(appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onForeground()
        // Back in front: a service Android stopped meanwhile starts again.
        notificationService.ensureRunning()
    }

    private fun open(intent: Intent?) {
        NotificationLinks.parse(intent?.dataString)?.let(appViewModel::onNotificationOpened)
    }
}
