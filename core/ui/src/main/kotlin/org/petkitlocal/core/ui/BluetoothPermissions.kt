package org.petkitlocal.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow

/**
 * The runtime permissions a Bluetooth sync with a fountain needs (issue #104): from Android 12,
 * the "nearby devices" pair (`BLUETOOTH_SCAN`, declared `neverForLocation`, and
 * `BLUETOOTH_CONNECT`); before it, on Android 10 and 11 (minSdk 29), the precise location a
 * Bluetooth scan requires there.
 */
object BluetoothPermissions {
    fun required(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun granted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Asks Android for [BluetoothPermissions] each time [requests] says so, and hands [onResult]
 * whether all of them were allowed.
 */
@Composable
fun BluetoothPermissionRequests(requests: Flow<Unit>, onResult: (granted: Boolean) -> Unit) {
    val latest by rememberUpdatedState(onResult)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
        latest(answers.isNotEmpty() && answers.values.all { it })
    }
    LaunchedEffect(requests) {
        requests.collect { ask.launch(BluetoothPermissions.required()) }
    }
}
