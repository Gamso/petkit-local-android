package org.petkitlocal.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.petkitlocal.feature.auth.navigateToSignIn
import org.petkitlocal.feature.auth.signInScreen
import org.petkitlocal.feature.device.deviceScreen
import org.petkitlocal.feature.device.navigateToDevice
import org.petkitlocal.feature.history.historyScreen
import org.petkitlocal.feature.history.navigateToHistory
import org.petkitlocal.feature.home.HomeDestination
import org.petkitlocal.feature.home.HomeShortcut
import org.petkitlocal.feature.home.homeScreen
import org.petkitlocal.feature.live.liveScreen
import org.petkitlocal.feature.live.navigateToLive
import org.petkitlocal.feature.pets.navigateToPets
import org.petkitlocal.feature.pets.petsScreen
import org.petkitlocal.feature.settings.navigateToSettings
import org.petkitlocal.feature.settings.settingsScreen

/**
 * The app's one navigation graph, with type-safe destinations owned by each feature. Features do
 * not know each other; the app wires one to the next here.
 */
@Composable
fun PetkitNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = HomeDestination, modifier = modifier) {
        homeScreen(
            onShortcut = { shortcut ->
                when (shortcut) {
                    HomeShortcut.SignIn -> navController.navigateToSignIn()
                    HomeShortcut.Device -> navController.navigateToDevice(deviceId = PLACEHOLDER_DEVICE_ID)
                    HomeShortcut.History -> navController.navigateToHistory()
                    HomeShortcut.Pets -> navController.navigateToPets()
                    HomeShortcut.Live -> navController.navigateToLive(deviceId = PLACEHOLDER_DEVICE_ID)
                    HomeShortcut.Settings -> navController.navigateToSettings()
                }
            },
        )
        signInScreen(onBack = back)
        deviceScreen(onBack = back)
        historyScreen(onBack = back)
        petsScreen(onBack = back)
        liveScreen(onBack = back)
        settingsScreen(onBack = back)
    }
}

/** Until Home lists real devices (M1), its shortcut opens device 1 to prove the argument. */
private const val PLACEHOLDER_DEVICE_ID = 1L
