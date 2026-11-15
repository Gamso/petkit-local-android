package org.petkitlocal.feature.device.feeder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import org.petkitlocal.core.domain.feeder.MealState
import org.petkitlocal.core.domain.feeder.PlanProblem
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.feature.device.R

/* The feeder page's words: times, days, portions and meal states, in the app's language. */

@Composable
@ReadOnlyComposable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

/** A time of day as the locale writes it: `6:30 PM`, `18:30`. */
internal fun clockText(time: LocalTime, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

/** "Today, 18:00", "Tomorrow, 07:00", or the weekday's name further on. */
@Composable
internal fun whenText(at: Instant, today: LocalDate, zone: ZoneId): String {
    val locale = locale()
    val local = at.atZone(zone)
    val time = clockText(local.toLocalTime(), locale)
    return when (local.toLocalDate()) {
        today -> stringResource(R.string.feeder_today_at, time)

        today.plusDays(1) -> stringResource(R.string.feeder_tomorrow_at, time)

        else -> stringResource(
            R.string.feeder_day_at,
            local.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase(locale) },
            time,
        )
    }
}

/** A day's name, for the plan editor's chips and its messages. */
internal fun dayName(day: DayOfWeek, locale: Locale, short: Boolean = false): String =
    day.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale).replaceFirstChar { it.uppercase(locale) }

/** "2 portions", or "Hopper 1: 1 · Hopper 2: 0". */
@Composable
internal fun portionsText(portions: Portions): String = when (portions) {
    is Portions.Single -> pluralStringResource(R.plurals.feeder_portions, portions.amount, portions.amount)
    is Portions.Dual -> stringResource(R.string.feeder_hoppers, portions.hopper1, portions.hopper2)
}

@Composable
internal fun mealStateText(state: MealState): String = stringResource(
    when (state) {
        MealState.Served -> R.string.feeder_meal_served
        MealState.Skipped -> R.string.feeder_meal_skipped
        MealState.Upcoming -> R.string.feeder_meal_upcoming
        MealState.Paused -> R.string.feeder_meal_paused
    },
)

@Composable
internal fun problemText(problem: PlanProblem): String = when (problem) {
    is PlanProblem.NoDays -> stringResource(R.string.feeder_problem_no_days)
    is PlanProblem.DayTwice -> stringResource(R.string.feeder_problem_day_twice, dayName(problem.day, locale()))
    is PlanProblem.SameTime -> stringResource(R.string.feeder_problem_same_time, clockText(problem.time, locale()))
    is PlanProblem.NothingServed -> stringResource(R.string.feeder_problem_nothing)
    is PlanProblem.TooManyMeals -> stringResource(R.string.feeder_problem_too_many)
}
