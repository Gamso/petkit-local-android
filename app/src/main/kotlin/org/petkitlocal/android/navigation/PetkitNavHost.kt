package org.petkitlocal.android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.petkitlocal.android.AppViewModel
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.feature.auth.SignInDestination
import org.petkitlocal.feature.auth.signInScreen
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.deviceScreen
import org.petkitlocal.feature.device.deviceSettingsScreen
import org.petkitlocal.feature.device.navigateToDevice
import org.petkitlocal.feature.device.navigateToDeviceSettings
import org.petkitlocal.feature.history.historyScreen
import org.petkitlocal.feature.history.navigateToHistory
import org.petkitlocal.feature.home.HomeDestination
import org.petkitlocal.feature.home.HomeNavigation
import org.petkitlocal.feature.home.homeScreen
import org.petkitlocal.feature.live.liveScreen
import org.petkitlocal.feature.pets.navigateToPets
import org.petkitlocal.feature.pets.petsScreen
import org.petkitlocal.feature.settings.navigateToSettings
import org.petkitlocal.feature.settings.settingsScreen

/**
 * The auth gate: nothing until the stored session has been read, then the sign-in screen
 * without a session and the app with one. It follows the session both ways, so a session ended
 * anywhere (sign-out, a 401, a revoked token found at start) lands on sign-in, which says why.
 */
@Composable
fun PetkitApp(viewModel: AppViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    if (authState == AuthState.Unknown) {
        // A few milliseconds at start, while the session is read: the window's own background.
        Box(Modifier.fillMaxSize().background(PkTheme.colors.background))
    } else {
        val signedIn = authState is AuthState.SignedIn
        if (signedIn) LiveUpdates(viewModel)
        PetkitNavHost(signedIn = signedIn)
    }
}

/**
 * Keeps the live updates going while the app is signed in and visible: collection stops when the
 * activity stops, and the socket closes a few seconds later, so the background costs no battery
 * (SPEC.md §6). Every screen behind the gate shows devices, pets or their history, which `/ws`
 * keeps current; a screen that shows whether it is live collects the same shared socket.
 */
@Composable
private fun LiveUpdates(viewModel: AppViewModel) {
    viewModel.liveUpdates.collectAsStateWithLifecycle(initialValue = LiveUpdatesStatus.Connecting)
}

/**
 * The app's one navigation graph, with type-safe destinations owned by each feature. Features do
 * not know each other; the app wires one to the next here.
 */
@Composable
fun PetkitNavHost(
    signedIn: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val back: () -> Unit = { navController.popBackStack() }
    // The start is chosen once; later changes of the session navigate (below).
    val start: Any = remember { if (signedIn) HomeDestination else SignInDestination }
    NavHost(navController = navController, startDestination = start, modifier = modifier) {
        signInScreen()
        homeScreen(
            HomeNavigation(
                onDeviceClick = { deviceId -> navController.navigateToDevice(deviceId) },
                onOpenPets = { navController.navigateToPets() },
                onOpenHistory = { navController.navigateToHistory() },
                onOpenSettings = { navController.navigateToSettings() },
            ),
        )
        deviceScreen(
            DeviceNavigation(
                onBack = back,
                onOpenSettings = { deviceId -> navController.navigateToDeviceSettings(deviceId) },
                onOpenHistory = { navController.navigateToHistory() },
            ),
        )
        deviceSettingsScreen(onBack = back)
        historyScreen(onBack = back)
        petsScreen(onBack = back)
        liveScreen(onBack = back)
        settingsScreen(onBack = back)
    }
    LaunchedEffect(signedIn) { navController.followSession(signedIn) }
}

/**
 * Replaces the whole back stack: no way "back" into the app after the session ended, or to
 * sign-in after it started.
 */
internal fun NavController.followSession(signedIn: Boolean) {
    val onSignIn = currentDestination?.hasRoute<SignInDestination>() == true
    if (signedIn != onSignIn) return
    navigate(if (signedIn) HomeDestination else SignInDestination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
