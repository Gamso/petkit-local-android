package org.petkitlocal.core.domain.notifications

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.settings.NotificationKinds

/** Whether notifications are on for this phone. */
class ObserveNotificationsEnabledUseCase @Inject constructor(
    private val preferences: NotificationPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = preferences.enabled
}

/** Turns notifications on or off for this phone; the service follows. */
class SetNotificationsEnabledUseCase @Inject constructor(private val preferences: NotificationPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = preferences.setEnabled(enabled)
}

/**
 * Whether the notification service should hold its socket: signed in and notifications on. Says
 * nothing until the stored session has been read at start-up, so a service started at boot does
 * not stop for a session it has not read yet.
 */
class ObserveNotificationServiceWantedUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val preferences: NotificationPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        combine(auth.authState.filter { it != AuthState.Unknown }, preferences.enabled) { state, enabled ->
            state is AuthState.SignedIn && enabled
        }.distinctUntilChanged()
}

/**
 * What a notification is about, by name: the device (its model, which is what the app calls it)
 * and the pet. Read from the cache; a device the cache does not know yet is fetched once.
 */
data class NotificationSubject(val device: Device?, val petName: String?)

class DescribeNotificationUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
) {
    suspend operator fun invoke(notification: ServerNotification): NotificationSubject {
        val deviceId = notification.deviceId
        var device = deviceId?.let { devices.observeDevice(it).first() }
        if (deviceId != null && device == null && devices.refresh() is Outcome.Success) {
            device = devices.observeDevice(deviceId).first()
        }
        val petName = notification.petId?.let { id -> pets.observePets().first().firstOrNull { it.id == id }?.name }
        return NotificationSubject(device, petName)
    }
}

/** Where tapping a notification leads. */
sealed interface NotificationTarget {
    /** The app's start: nothing more precise to show. */
    data object Home : NotificationTarget

    data class Device(val deviceId: Long) : NotificationTarget

    /** History of [deviceId] on [day], the local day it happened. */
    data class History(val deviceId: Long, val day: LocalDate) : NotificationTarget

    companion object {
        /** Events that are History entries: a visit, a meal, a fault the device reported as an event. */
        private val EVENT_KINDS = setOf(
            NotificationKinds.TOILET,
            NotificationKinds.FEED_SERVED,
            NotificationKinds.FEED_FAILED,
            NotificationKinds.ERROR,
        )

        /** The device's own fault text, a state condition rather than an event (`PushMessage.code`). */
        private const val ERROR_STATE_KEY = "errorMsg"

        /**
         * An event opens its History entry, on the day it happened in [zone]; a state condition
         * (food low, a consumable, offline, a fault the device states) opens the device, where it
         * shows; no device, the start.
         */
        fun of(notification: ServerNotification, zone: ZoneId): NotificationTarget {
            val deviceId = notification.deviceId ?: return Home
            val event = notification.kind in EVENT_KINDS && notification.code != ERROR_STATE_KEY
            if (!event) return Device(deviceId)
            val day = notification.time.atZone(zone).toLocalDate()
            return History(deviceId, day)
        }
    }
}
