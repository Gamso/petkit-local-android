package org.petkitlocal.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.petkitlocal.android.navigation.PetkitNavHost
import org.petkitlocal.core.designsystem.theme.PkTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PkTheme {
                PetkitNavHost()
            }
        }
    }
}
