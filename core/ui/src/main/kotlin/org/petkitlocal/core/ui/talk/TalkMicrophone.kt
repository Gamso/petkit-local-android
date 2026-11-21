package org.petkitlocal.core.ui.talk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.ui.startFirst

/**
 * Asks Android for the microphone each time [requests] says so (a [TalkController]'s
 * `requestMicrophone`), and hands the answer to [onResult].
 */
@Composable
fun MicrophoneRequests(requests: Flow<Unit>, onResult: (granted: Boolean) -> Unit) {
    val latest by rememberUpdatedState(onResult)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest(it) }
    LaunchedEffect(requests) {
        requests.collect { ask.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

/** This app's page in Android's settings, where a refused microphone can be allowed. */
fun openAppSettings(context: Context) {
    startFirst(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}
