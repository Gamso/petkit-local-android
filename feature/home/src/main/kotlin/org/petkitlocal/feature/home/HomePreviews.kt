package org.petkitlocal.feature.home

import androidx.compose.runtime.Composable
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.home.AlertKind
import org.petkitlocal.core.domain.home.BusyActivity
import org.petkitlocal.core.domain.home.DayEvent
import org.petkitlocal.core.domain.home.DayEventType
import org.petkitlocal.core.domain.home.DeviceAlert
import org.petkitlocal.core.domain.home.DeviceOverview
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.home.Headline
import org.petkitlocal.core.domain.home.HomeOverview
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.home.Quantity
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Pet

// The IDE previews of Home. The screenshot tests (src/test) render the whole mock household.

private fun previewDevice(id: Long, modelName: String, family: DeviceFamily) =
    Device(id, "x", modelName, family, online = true, DeviceTransport.Http, null, null, isCamera = false)

private val previewOverview = HomeOverview(
    devices = listOf(
        DeviceOverview(
            previewDevice(1, "Pura Max", DeviceFamily.LitterBox),
            Headline.Visits(3),
            DeviceStatus.Idle,
            listOf(DayEvent(380, DayEventType.Visit, 1, 186), DayEvent(465, DayEventType.Visit, 2, 95)),
        ),
        DeviceOverview(
            previewDevice(2, "Fresh Element Solo", DeviceFamily.Feeder),
            Headline.Served(Quantity(40.0, "g"), meals = 2),
            DeviceStatus.Busy(BusyActivity.Feeding),
            listOf(DayEvent(420, DayEventType.Meal, null, null)),
        ),
    ),
    pets = listOf(PetOverview(Pet(1, "Mochi", listOf(1), 4200.0, 4333.0, true, null), colorIndex = 0, visitsToday = 2)),
    alerts = listOf(DeviceAlert(1, "Pura Max", AlertKind.Problem("waste_bin", "Waste Bin Full"))),
)

@PkPreviews
@Composable
private fun HomeScreenPreview() = PkTheme {
    HomeScreen(HomeUiState(overview = previewOverview), onRefresh = {}, navigation = HomeNavigation())
}
