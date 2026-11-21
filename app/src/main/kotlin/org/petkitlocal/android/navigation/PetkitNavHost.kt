package org.petkitlocal.android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.petkitlocal.android.AppViewModel
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.notifications.NotificationTarget
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.feature.auth.CertificateChangeRoute
import org.petkitlocal.feature.auth.SignInDestination
import org.petkitlocal.feature.auth.password.changePasswordScreen
import org.petkitlocal.feature.auth.password.navigateToChangePassword
import org.petkitlocal.feature.auth.signInScreen
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.deviceScreen
import org.petkitlocal.feature.device.deviceSettingsScreen
import org.petkitlocal.feature.device.navigateToDevice
import org.petkitlocal.feature.device.navigateToDeviceSettings
import org.petkitlocal.feature.history.clipScreen
import org.petkitlocal.feature.history.historyScreen
import org.petkitlocal.feature.history.navigateToClip
import org.petkitlocal.feature.history.navigateToHistory
import org.petkitlocal.feature.home.HomeDestination
import org.petkitlocal.feature.home.HomeNavigation
import org.petkitlocal.feature.home.homeScreen
import org.petkitlocal.feature.live.liveScreen
import org.petkitlocal.feature.pets.PetsNavigation
import org.petkitlocal.feature.pets.closeDeletedPet
import org.petkitlocal.feature.pets.closePetEditor
import org.petkitlocal.feature.pets.navigateToPet
import org.petkitlocal.feature.pets.navigateToPetEditor
import org.petkitlocal.feature.pets.navigateToPetPhotos
import org.petkitlocal.feature.pets.navigateToPets
import org.petkitlocal.feature.pets.petsScreens
import org.petkitlocal.feature.settings.SettingsNavigation
import org.petkitlocal.feature.settings.licences.licencesScreen
import org.petkitlocal.feature.settings.licences.navigateToLicences
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
        val target by viewModel.openTarget.collectAsStateWithLifecycle()
        PetkitNavHost(signedIn = signedIn, target = target, onTargetHandled = viewModel::onTargetHandled)
        // The server's pinned certificate replaced by another: asked over whatever screen is up.
        if (signedIn) CertificateChangeRoute()
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
    target: NotificationTarget? = null,
    onTargetHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val back: () -> Unit = { navController.navigateBack() }
    // The start is chosen once; later changes of the session navigate (below).
    val start: Any = remember { if (signedIn) HomeDestination else SignInDestination }
    NavHost(navController = navController, startDestination = start, modifier = modifier) {
        signInScreen()
        homeScreen(
            HomeNavigation(
                onDeviceClick = { deviceId -> navController.navigateToDevice(deviceId) },
                onOpenPets = { navController.navigateToPets() },
                onOpenPet = { petId -> navController.navigateToPet(petId) },
                onOpenHistory = { navController.navigateToHistory() },
                onOpenSettings = { navController.navigateToSettings() },
            ),
        )
        deviceScreen(
            DeviceNavigation(
                onBack = back,
                onOpenSettings = { deviceId -> navController.navigateToDeviceSettings(deviceId) },
                onOpenHistory = { deviceId -> navController.navigateToHistory(deviceId = deviceId) },
                onOpenDevice = { deviceId -> navController.navigateToDevice(deviceId) },
                onOpenClip = { clip -> navController.navigateToClip(clip) },
            ),
        )
        deviceSettingsScreen(onBack = back)
        historyScreen(onBack = back)
        clipScreen(onBack = back)
        petsScreens(
            PetsNavigation(
                onBack = back,
                onOpenPet = { petId -> navController.navigateToPet(petId) },
                onOpenHistory = { petId, day -> navController.navigateToHistory(petId = petId, day = day) },
                onAddPet = { navController.navigateToPetEditor() },
                onEditPet = { petId -> navController.navigateToPetEditor(petId) },
                onOpenPhotos = { petId -> navController.navigateToPetPhotos(petId) },
                onPetSaved = { petId, created -> navController.closePetEditor(petId, created) },
                onPetDeleted = { navController.closeDeletedPet() },
            ),
        )
        liveScreen(
            onBack = back,
            onOpenClip = { clip -> navController.navigateToClip(clip) },
            onOpenHistory = { deviceId -> navController.navigateToHistory(deviceId = deviceId) },
        )
        settingsScreen(
            SettingsNavigation(
                onBack = back,
                onChangePassword = { navController.navigateToChangePassword() },
                onOpenLicences = { navController.navigateToLicences() },
            ),
        )
        changePasswordScreen(onBack = back)
        licencesScreen(onBack = back)
    }
    LaunchedEffect(signedIn) { navController.followSession(signedIn) }
    // After followSession: a tapped notification opens its page over Home, so back leads there.
    LaunchedEffect(signedIn, target) {
        if (target == null) return@LaunchedEffect
        if (signedIn) navController.open(target)
        onTargetHandled()
    }
}

/**
 * The way back of every page (its back arrow, and any page that closes itself): one page back,
 * only from a page that is settled on screen, and never past the first page.
 *
 * A bare `popBackStack()` did neither. The page being left stays on screen, its back arrow taking
 * taps, for the whole exit transition, so a second quick tap popped again: from a page opened from
 * Home, Home itself went, and the NavHost was left with nothing to draw, an empty page. Once a pop
 * has started, the new top entry is not RESUMED until its transition has finished, which is what
 * drops that second tap. The system back needs none of this: the NavHost's own handler never pops
 * the last entry, and Android closes the app from it.
 */
internal fun NavController.navigateBack() {
    val top = currentBackStackEntry ?: return
    if (top.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (previousBackStackEntry == null) return
    popBackStack()
}

/** A notification's page: its device, or its device's History on the day it happened. */
internal fun NavController.open(target: NotificationTarget) {
    when (target) {
        NotificationTarget.Home -> Unit
        is NotificationTarget.Device -> navigateToDevice(target.deviceId)
        is NotificationTarget.History -> navigateToHistory(deviceId = target.deviceId, day = target.day)
    }
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
