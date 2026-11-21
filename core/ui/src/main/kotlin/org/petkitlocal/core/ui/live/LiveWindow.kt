package org.petkitlocal.core.ui.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/*
 * What a live picture does to the window (the live screen, a camera page's full screen): light
 * system-bar icons over its dark background, and, full screen, landscape with the system bars
 * hidden until swiped in. Undone when it leaves. The activity declares the configuration changes,
 * so turning the phone keeps the player (and its socket) instead of building a new one.
 */

/** Light status and navigation bar icons while the screen is shown: it is always dark. */
@Composable
fun DarkSystemBars() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

/** Full screen: landscape (either way up), system bars hidden until swiped in. */
@Composable
fun ImmersiveLandscape(on: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, on) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (on) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (on) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
