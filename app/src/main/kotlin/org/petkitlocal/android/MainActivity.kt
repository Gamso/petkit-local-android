package org.petkitlocal.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.petkitlocal.android.navigation.PetkitApp
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * The one activity. Its language is the phone's, or the one picked for the app in Android 13+'s
 * own settings: Android applies either, nothing here does.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PkTheme {
                PetkitApp(appViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onForeground()
    }
}
