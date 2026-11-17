package org.petkitlocal.feature.home

import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.home.HomeOverview
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.testing.Household

/** Home's states, built from the shared household by the real rules. */
object HomeFixtures {
    val overview: HomeOverview =
        HomeRules.overview(Household.devices, Household.pets, Household.todayItems, Household.zone)

    val content = HomeUiState(overview = overview)

    /** Notifications on, but Android 13+ does not let them show: the banner above the alerts. */
    val notificationsBlocked = content.copy(askNotifications = true)

    val offline = HomeUiState(overview = overview, refreshError = DomainError.Network("timeout"))

    val unreachableEmpty = HomeUiState(
        overview = HomeOverview(emptyList(), emptyList(), emptyList()),
        refreshError = DomainError.Network(),
    )

    val loading = HomeUiState()
}
