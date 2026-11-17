package org.petkitlocal.core.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.petkitlocal.core.datastore.NotificationStore
import org.petkitlocal.core.domain.notifications.NotificationPreferencesRepository
import org.petkitlocal.core.domain.notifications.NotificationPrompts

/** Notifications on this phone, and what the user was asked for, in [NotificationStore]. */
class NotificationPreferencesRepositoryImpl @Inject constructor(private val store: NotificationStore) :
    NotificationPreferencesRepository {
    override val enabled: Flow<Boolean> = store.enabled

    override suspend fun setEnabled(enabled: Boolean) = store.setEnabled(enabled)

    override val asked: Flow<NotificationPrompts> =
        combine(store.permissionAsked, store.batteryAsked, store.homeBannerDismissed) { permission, battery, banner ->
            NotificationPrompts(permission = permission, battery = battery, homeBanner = banner)
        }

    override suspend fun markPermissionAsked() = store.markPermissionAsked()

    override suspend fun markBatteryAsked() = store.markBatteryAsked()

    override suspend fun markHomeBannerDismissed() = store.markHomeBannerDismissed()
}
