package org.petkitlocal.core.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/**
 * Asks for Android 13+'s notification permission, the one way every screen asks (Settings, the
 * banner on Home): Android's own prompt, or, where Android would show none (before Android 13, or
 * once the permission was refused for good after [askedBefore]), the app's notification settings
 * in Android. [onResult] gets Android's answer to a prompt; settings opened answer nothing, and the
 * screen reads the permission again when it comes back to the front.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): (askedBefore: Boolean) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(context, activity, launcher) {
        { askedBefore ->
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> openNotificationSettings(context)
                askedBefore && !canAskAgain(activity) -> openNotificationSettings(context)
                else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private fun canAskAgain(activity: Activity?): Boolean = activity != null &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

/** This app's notification settings in Android, where a refused permission can be granted. */
private fun openNotificationSettings(context: Context) {
    startFirst(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

/** Starts the first of [intents] the phone can open; none, and the screen's own note stays. */
fun startFirst(context: Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
}
