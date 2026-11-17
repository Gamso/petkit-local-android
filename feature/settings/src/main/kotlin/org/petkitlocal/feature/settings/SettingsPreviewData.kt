package org.petkitlocal.feature.settings

import org.petkitlocal.core.domain.settings.NotificationKinds
import org.petkitlocal.core.domain.settings.NotificationSettings
import org.petkitlocal.core.domain.settings.ServerNotificationKind

/** States of the settings screen for previews and screenshot tests. */
internal object SettingsPreviewData {
    private val notifications = NotificationSettings(
        chosen = NotificationKinds.KNOWN - NotificationKinds.FEED_SERVED,
        serverKinds = NotificationKinds.KNOWN.map {
            ServerNotificationKind(it, enabledOnServer = it != NotificationKinds.OFFLINE)
        },
        connected = false,
        lastId = 0,
    )

    /** Everything read; meals served turned off here, devices offline turned off in the panel. */
    val loaded = SettingsUiState(
        server = "https://pets.example.com",
        details = Loaded.Ready(ServerDetails(version = "1.4.0", phoneName = "Google Pixel 8")),
        notifications = Loaded.Ready(notifications),
        appVersion = "0.1.0",
    )

    /** The server out of reach: nothing read, a switch flip that did not get through. */
    val unreachable = SettingsUiState(
        server = "https://pets.example.com",
        details = Loaded.Failed,
        notifications = Loaded.Failed,
        appVersion = "0.1.0",
    )

    /** A switch being written. */
    val saving = loaded.copy(savingKind = NotificationKinds.TOILET)
}
