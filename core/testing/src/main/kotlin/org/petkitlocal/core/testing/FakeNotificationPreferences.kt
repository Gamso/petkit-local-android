package org.petkitlocal.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import org.petkitlocal.core.domain.notifications.NotificationAccess
import org.petkitlocal.core.domain.notifications.NotificationPreferencesRepository
import org.petkitlocal.core.domain.notifications.NotificationPrompts

/** This phone's notification preferences, in memory: on, nothing asked, until a test says otherwise. */
class FakeNotificationPreferencesRepository(
    enabled: Boolean = true,
    asked: NotificationPrompts = NotificationPrompts(),
) : NotificationPreferencesRepository {
    override val enabled = MutableStateFlow(enabled)
    override val asked = MutableStateFlow(asked)

    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override suspend fun markPermissionAsked() {
        asked.value = asked.value.copy(permission = true)
    }

    override suspend fun markBatteryAsked() {
        asked.value = asked.value.copy(battery = true)
    }

    override suspend fun markHomeBannerDismissed() {
        asked.value = asked.value.copy(homeBanner = true)
    }
}

/** What Android allows, as a test sets it; read at each call, as the real one is. */
class FakeNotificationAccess(var permissionNeeded: Boolean = false, var batteryOptimised: Boolean = false) :
    NotificationAccess {
    override fun permissionNeeded(): Boolean = permissionNeeded

    override fun batteryOptimised(): Boolean = batteryOptimised
}
