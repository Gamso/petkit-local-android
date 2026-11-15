package org.petkitlocal.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.home.AlertKind
import org.petkitlocal.core.domain.home.BusyActivity
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.ui.ServerLabels

/*
 * Home's words for what the server names in English. Entity names and option labels go through
 * core:ui's ServerLabels, keyed by entity key or option, with the server's English as the
 * fallback (SPEC.md §7). Home adds only what no server label says: short consumable names for its
 * banners and figures.
 */

/** The title of an alert banner, without the device name. */
@Composable
internal fun alertTitle(kind: AlertKind, labels: ServerLabels): String = when (kind) {
    is AlertKind.Error -> stringResource(R.string.home_status_error, kind.message)

    AlertKind.Offline -> stringResource(R.string.home_alert_offline)

    is AlertKind.Problem -> labels.entity(kind.entityKey, kind.entityName)

    is AlertKind.Empty -> stringResource(R.string.home_alert_empty, labels.entity(kind.entityKey, kind.entityName))

    is AlertKind.RunningOut -> pluralStringResource(
        R.plurals.home_alert_days_left,
        kind.daysLeft,
        consumableLabel(kind.entityKey)?.let { stringResource(it) } ?: labels.entity(kind.entityKey, kind.entityName),
        kind.daysLeft,
    )

    is AlertKind.Low -> stringResource(
        R.string.home_alert_low,
        consumableLabel(kind.entityKey)?.let { stringResource(it) } ?: labels.entity(kind.entityKey, kind.entityName),
        kind.percent,
    )
}

/** A consumable's short name, by its entity key; the entity names say "N50 Days Left" and the like. */
@StringRes
private fun consumableLabel(key: String): Int? = when {
    key.startsWith("desiccant") -> R.string.home_consumable_desiccant
    key.startsWith("n50") -> R.string.home_consumable_n50
    key.startsWith("n60") -> R.string.home_consumable_n60
    key.startsWith("litter") -> R.string.home_consumable_litter
    key.endsWith("filter") -> R.string.home_consumable_filter
    key.endsWith("liquid") -> R.string.home_consumable_liquid
    key.endsWith("battery") -> R.string.home_consumable_battery
    else -> null
}

/** The label above a [Headline.Level][org.petkitlocal.core.domain.home.Headline.Level] figure. */
@Composable
internal fun levelLabel(key: String, name: String, labels: ServerLabels): String =
    consumableLabel(key)?.let { stringResource(it) } ?: labels.entity(key, name)

/** What a status band says. */
@Composable
internal fun statusLabel(status: DeviceStatus, labels: ServerLabels): String = when (status) {
    DeviceStatus.Idle -> stringResource(R.string.home_status_idle)

    DeviceStatus.Offline -> stringResource(R.string.home_status_offline)

    is DeviceStatus.Error -> stringResource(R.string.home_status_error, status.message)

    is DeviceStatus.Busy -> when (val activity = status.activity) {
        BusyActivity.Feeding -> stringResource(R.string.home_status_feeding)

        BusyActivity.PetInside -> stringResource(R.string.home_status_pet_inside)

        // The work mode is a `device_status` option label: "cleaning", "odor removal"...
        is BusyActivity.Working -> labels.option(activity.mode).replaceFirstChar { it.uppercase() }
    }
}

/** One line on what went wrong with a refresh, for the banner or the error view. */
@Composable
internal fun refreshErrorMessage(error: DomainError): String = when (error) {
    is DomainError.Network -> stringResource(R.string.home_unreachable)
    is DomainError.SessionEnded, is DomainError.Unauthorized -> stringResource(R.string.home_session_ended)
    else -> stringResource(R.string.home_server_error)
}
