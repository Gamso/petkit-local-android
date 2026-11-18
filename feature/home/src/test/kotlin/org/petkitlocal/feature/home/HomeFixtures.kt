package org.petkitlocal.feature.home

import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.home.HomeOverview
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.model.DeviceFamily
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

    /**
     * The generations of one codename side by side (app issue #81), each under the name and with
     * the photo of the generation the server reports: a Pura Max (none reported), a Pura Max 2,
     * a Pura Max 3 (no photo: its glyph), a YumShare Dual-Hopper and Dual-Hopper 2, and a YumShare
     * Solo 2. Nothing else on the page, so the cards are what the eye compares.
     */
    val generations: HomeUiState = run {
        val puraMax = Household.litterBox
        val dualHopper = Household.dualFeeder
        val devices = listOf(
            puraMax,
            puraMax.copy(id = 1006, modelName = "Pura Max 2", generation = 2),
            puraMax.copy(id = 1007, modelName = "Pura Max 3", generation = 3),
            dualHopper,
            dualHopper.copy(id = 2005, modelName = "YumShare Dual-Hopper 2", generation = 2),
            Household.device(2003, "d4h", "YumShare Solo 2", DeviceFamily.Feeder, generation = 2),
        )
        val overview = HomeRules.overview(devices, emptyList(), emptyList(), Household.zone)
        HomeUiState(overview = overview.copy(alerts = emptyList()))
    }
}
