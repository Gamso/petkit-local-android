package org.petkitlocal.feature.device

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The device screen of one device. */
@Serializable
data class DeviceDestination(val deviceId: Long)

fun NavController.navigateToDevice(deviceId: Long) = navigate(DeviceDestination(deviceId))

fun NavGraphBuilder.deviceScreen(onBack: () -> Unit) {
    composable<DeviceDestination> { DeviceRoute(onBack = onBack) }
}
