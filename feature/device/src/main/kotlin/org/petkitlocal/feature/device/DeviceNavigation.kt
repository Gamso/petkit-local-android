package org.petkitlocal.feature.device

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.petkitlocal.feature.device.quickactions.QuickActionsHost
import org.petkitlocal.feature.device.settings.DeviceSettingsRoute

/** The screen of one device: its family's own, or the generic one ([DeviceRoute]). */
@Serializable
data class DeviceDestination(val deviceId: Long)

/** The settings of one device, from its settable entities. */
@Serializable
data class DeviceSettingsDestination(val deviceId: Long)

fun NavController.navigateToDevice(deviceId: Long) = navigate(DeviceDestination(deviceId))

fun NavController.navigateToDeviceSettings(deviceId: Long) = navigate(DeviceSettingsDestination(deviceId))

/** Where a device screen leads; the app wires each to a destination. */
data class DeviceNavigation(
    val onBack: () -> Unit = {},
    val onOpenSettings: (deviceId: Long) -> Unit = {},
    /** The litter box and feeder pages lead to History from their activity and action sheet. */
    val onOpenHistory: () -> Unit = {},
)

fun NavGraphBuilder.deviceScreen(navigation: DeviceNavigation) {
    // Every family's page gets the user's quick actions from the host around it (issue #40).
    composable<DeviceDestination> { QuickActionsHost { DeviceRoute(navigation) } }
}

fun NavGraphBuilder.deviceSettingsScreen(onBack: () -> Unit) {
    composable<DeviceSettingsDestination> { QuickActionsHost { DeviceSettingsRoute(onBack = onBack) } }
}

/** The [DeviceDestination] and [DeviceSettingsDestination] property the argument is stored under. */
internal const val DEVICE_ID_KEY = "deviceId"
