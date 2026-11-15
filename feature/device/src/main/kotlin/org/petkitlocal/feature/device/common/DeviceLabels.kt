package org.petkitlocal.feature.device.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.home.BusyActivity
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.feature.device.R

/*
 * The words of device screens for what the server sends as values, keys and errors. Entity and
 * action names and option labels go through core:ui's ServerLabels (by stable key, the server's
 * English as the fallback, SPEC.md §7); numbers, times and dates follow the locale.
 */

/** The locale screens format with: the app's, which is the phone's. */
@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** What a status line says. */
@Composable
internal fun statusText(status: DeviceStatus, labels: ServerLabels): String = when (status) {
    DeviceStatus.Idle -> stringResource(R.string.device_status_idle)

    DeviceStatus.Offline -> stringResource(R.string.device_status_offline)

    is DeviceStatus.Error -> stringResource(R.string.device_status_error, status.message)

    is DeviceStatus.Busy -> when (val activity = status.activity) {
        BusyActivity.Feeding -> stringResource(R.string.device_status_feeding)

        BusyActivity.PetInside -> stringResource(R.string.device_status_pet_inside)

        // A `device_status` option label: "cleaning", "odor removal"...
        is BusyActivity.Working -> optionText(activity.mode, labels)
    }
}

/**
 * An option label or enum value as a sentence-case phrase: translated by its key when the app
 * knows it, else the server's text with its underscores read as spaces (`do_not_flow`).
 */
internal fun optionText(value: String, labels: ServerLabels): String =
    labels.option(value).replace('_', ' ').replaceFirstChar { it.titlecase() }

/** How petkit-local passed a command on, in one sentence. */
@Composable
internal fun deliveryText(delivery: CommandDelivery): String = stringResource(
    when (delivery) {
        CommandDelivery.Mqtt -> R.string.device_feedback_mqtt
        CommandDelivery.Queued -> R.string.device_feedback_queued
        CommandDelivery.Local -> R.string.device_feedback_local
    },
)

/**
 * Why a command failed: the server's own message when it wrote one (SPEC.md §6, "the server's
 * error message"), the app's words otherwise.
 */
@Composable
internal fun errorText(error: DomainError): String = when (error) {
    is DomainError.Validation -> error.message.ifBlank { stringResource(R.string.device_error_refused) }

    is DomainError.NotFound -> error.message.ifBlank { stringResource(R.string.device_error_not_found) }

    is DomainError.AccessoryUnreachable ->
        error.relay
            ?.let { stringResource(R.string.device_error_accessory_unreachable, it) }
            ?: stringResource(R.string.device_error_accessory_unreachable_unnamed)

    is DomainError.Server -> error.message?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.device_error_server, error.status?.let { "HTTP $it" } ?: error.code.orEmpty())

    is DomainError.Network -> stringResource(R.string.device_error_network)

    is DomainError.SessionEnded, is DomainError.Unauthorized -> stringResource(R.string.device_error_session)

    is DomainError.RateLimited -> stringResource(R.string.device_error_rate_limited)

    is DomainError.UnexpectedResponse -> stringResource(R.string.device_error_unexpected)
}

/** What is wrong with a value in an editor, said next to it. */
@Composable
internal fun problemText(problem: EntityWriteProblem, entity: Entity): String = when (problem) {
    is EntityWriteProblem.BelowMin -> stringResource(R.string.device_problem_below_min, numberText(problem.min, entity))

    is EntityWriteProblem.AboveMax -> stringResource(R.string.device_problem_above_max, numberText(problem.max, entity))

    is EntityWriteProblem.TooLong ->
        pluralStringResource(R.plurals.device_problem_too_long, problem.maxLength, problem.maxLength)

    EntityWriteProblem.InvalidTime -> stringResource(R.string.device_problem_invalid_time)

    EntityWriteProblem.NotAnOption -> stringResource(R.string.device_problem_not_an_option)

    EntityWriteProblem.WrongType -> stringResource(R.string.device_problem_wrong_type)

    EntityWriteProblem.NotSettable -> stringResource(R.string.device_problem_not_settable)
}
