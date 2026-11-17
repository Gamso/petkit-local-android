package org.petkitlocal.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.domain.settings.NotificationKinds

/** The server: where it is, and what it runs. */
@Composable
internal fun ServerSection(uiState: SettingsUiState, onRetry: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_server)) {
        InfoRow(
            name = stringResource(R.string.settings_address),
            help = stringResource(R.string.settings_address_help),
            value = uiState.server.orEmpty(),
        )
        val details = uiState.details
        InfoRow(
            name = stringResource(R.string.settings_server_version),
            help = stringResource(R.string.settings_server_version_help),
            value = when (details) {
                Loaded.Loading -> stringResource(R.string.settings_loading)
                Loaded.Failed -> stringResource(R.string.settings_unknown)
                is Loaded.Ready -> details.value.version ?: stringResource(R.string.settings_unknown)
            },
            modifier = Modifier.testTag(SettingsTestTags.SERVER_VERSION),
        )
        if (details == Loaded.Failed) RetryNote(stringResource(R.string.settings_server_unreachable), onRetry)
    }
}

/**
 * The kinds of notification this phone gets. The server keeps the choice with this phone's
 * session and sends only those; a kind switched off in the panel reaches no phone, which its
 * line then says. Showing them is the notification service's job (issue #23).
 */
@Composable
internal fun NotificationsSection(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsSection(
        stringResource(R.string.settings_section_notifications),
        Modifier.testTag(SettingsTestTags.NOTIFICATIONS),
    ) {
        SectionNote(stringResource(R.string.settings_notifications_intro))
        when (val notifications = uiState.notifications) {
            Loaded.Loading -> SectionNote(stringResource(R.string.settings_loading))

            Loaded.Failed -> RetryNote(
                stringResource(R.string.settings_notifications_unreachable),
                actions.onRetry,
                Modifier.testTag(SettingsTestTags.NOTIFICATIONS_ERROR),
            )

            is Loaded.Ready -> {
                val settings = notifications.value
                settings.serverKinds.forEach { serverKind ->
                    val kind = serverKind.kind
                    SwitchRow(
                        name = kindName(kind),
                        help = if (serverKind.enabledOnServer) {
                            kindHelp(kind)
                        } else {
                            stringResource(R.string.settings_kind_off_on_server)
                        },
                        checked = settings.receives(kind),
                        enabled = uiState.savingKind == null,
                        saving = uiState.savingKind == kind,
                        onToggle = { on -> actions.onKindToggle(kind, on) },
                        modifier = Modifier.testTag(SettingsTestTags.kind(kind)),
                    )
                }
                if (uiState.kindNotSaved) SectionNote(stringResource(R.string.settings_kind_not_saved), error = true)
            }
        }
    }
}

/** This phone: its name on the server. */
@Composable
internal fun PhoneSection(uiState: SettingsUiState) {
    SettingsSection(stringResource(R.string.settings_section_phone)) {
        val details = uiState.details
        InfoRow(
            name = stringResource(R.string.settings_phone_name),
            help = stringResource(R.string.settings_phone_name_help),
            value = when (details) {
                Loaded.Loading -> stringResource(R.string.settings_loading)
                Loaded.Failed -> stringResource(R.string.settings_unknown)
                is Loaded.Ready -> details.value.phoneName ?: stringResource(R.string.settings_unknown)
            },
            modifier = Modifier.testTag(SettingsTestTags.PHONE_NAME),
        )
    }
}

/** The password and the session. */
@Composable
internal fun AccountSection(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsSection(stringResource(R.string.settings_section_account)) {
        LinkRow(
            name = stringResource(R.string.settings_change_password),
            help = stringResource(R.string.settings_change_password_help),
            onClick = actions.onChangePassword,
            modifier = Modifier.testTag(SettingsTestTags.CHANGE_PASSWORD),
        )
        SettingTitle(
            name = stringResource(R.string.settings_sign_out),
            help = stringResource(R.string.settings_sign_out_help),
        )
        PkButton(
            stringResource(if (uiState.signingOut) R.string.settings_signing_out else R.string.settings_sign_out),
            onClick = actions.onSignOutClick,
            style = PkButtonStyle.Danger,
            enabled = !uiState.signingOut,
            modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.SIGN_OUT),
        )
    }
}

/**
 * The app's version, that it is not PetKit's (no PetKit logo or image anywhere), its own licence,
 * and the open-source components it ships with theirs.
 */
@Composable
internal fun AboutSection(uiState: SettingsUiState, actions: SettingsActions) {
    SettingsSection(stringResource(R.string.settings_section_about)) {
        InfoRow(
            name = stringResource(R.string.settings_app_version),
            help = stringResource(R.string.settings_app_version_help),
            value = uiState.appVersion,
            modifier = Modifier.testTag(SettingsTestTags.APP_VERSION),
        )
        SectionNote(stringResource(R.string.settings_not_affiliated))
        LinkRow(
            name = stringResource(R.string.settings_app_licence),
            help = uiState.appLicence?.let { stringResource(R.string.settings_app_licence_help, it.name) },
            onClick = actions.onAppLicenceClick,
            modifier = Modifier.testTag(SettingsTestTags.APP_LICENCE),
        )
        LinkRow(
            name = stringResource(R.string.settings_licences),
            help = stringResource(R.string.settings_licences_help),
            onClick = actions.onOpenLicences,
            modifier = Modifier.testTag(SettingsTestTags.LICENCES),
        )
    }
}

@Composable
private fun kindName(kind: String): String = KIND_NAMES[kind]?.let { stringResource(it) } ?: kind

@Composable
private fun kindHelp(kind: String): String? = KIND_HELP[kind]?.let { stringResource(it) }

/** By the server's stable keys; a kind a newer server adds shows its key and no line. */
private val KIND_NAMES: Map<String, Int> = mapOf(
    NotificationKinds.TOILET to R.string.settings_kind_toilet,
    NotificationKinds.FEED_SERVED to R.string.settings_kind_feed_served,
    NotificationKinds.FEED_FAILED to R.string.settings_kind_feed_failed,
    NotificationKinds.ERROR to R.string.settings_kind_error,
    NotificationKinds.FOOD_LOW to R.string.settings_kind_food_low,
    NotificationKinds.CONSUMABLE to R.string.settings_kind_consumable,
    NotificationKinds.OFFLINE to R.string.settings_kind_offline,
)

private val KIND_HELP: Map<String, Int> = mapOf(
    NotificationKinds.TOILET to R.string.settings_kind_toilet_help,
    NotificationKinds.FEED_SERVED to R.string.settings_kind_feed_served_help,
    NotificationKinds.FEED_FAILED to R.string.settings_kind_feed_failed_help,
    NotificationKinds.ERROR to R.string.settings_kind_error_help,
    NotificationKinds.FOOD_LOW to R.string.settings_kind_food_low_help,
    NotificationKinds.CONSUMABLE to R.string.settings_kind_consumable_help,
    NotificationKinds.OFFLINE to R.string.settings_kind_offline_help,
)
