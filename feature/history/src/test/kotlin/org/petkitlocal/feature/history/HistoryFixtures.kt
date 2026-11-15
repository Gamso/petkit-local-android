package org.petkitlocal.feature.history

import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.history.HistoryEntry
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.EventField
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.testing.Household

/** History's fixtures: the household's morning, with weights and a camera box's media. */
@Suppress("MagicNumber") // Fixture data.
object HistoryFixtures {
    private const val MEDIA = "https://pets.example/app/v1/media/t5/2026-11-15"

    private fun file(kind: MediaKind, name: String) =
        MediaFile(kind, "$MEDIA/$name", "https://pets.example/app/v1/media/thumb/t5/2026-11-15/$name")

    val snapshot = file(MediaKind.Snapshot, "12_snapshot.jpg")
    val waste = file(MediaKind.Waste, "12_waste.jpg")
    val highlight = file(MediaKind.Highlight, "12_highlight.mp4")
    val playback = file(MediaKind.Playback, "12_playback.mp4")

    private val cameraMedia = TimelineMedia(listOf(snapshot, waste, highlight, playback))

    /** The morning's cards, newest first; the camera box's visit has its photos and clips. */
    val items: List<TimelineItem> = Household.todayItems.map { it.withKind() }.map { item ->
        when (item.id) {
            12L -> item.copy(weightG = 4310, media = cameraMedia)
            13L -> item.copy(media = TimelineMedia(listOf(snapshot)))
            14L -> item.copy(weightG = 6020)
            16L -> item.copy(weightG = 4290, media = TimelineMedia(emptyList(), videoPending = true))
            10L -> item.copy(weightG = 6080)
            else -> item
        }
    }

    /** The event kind the server names each card with, from its chip. */
    private fun TimelineItem.withKind(): TimelineItem = copy(
        eventKind = when {
            TimelineCategory.Toileting in categories -> "toilet_visit"
            TimelineCategory.Feeding in categories -> "feeding"
            TimelineCategory.Drinking in categories -> "drinking"
            TimelineCategory.Cleaning in categories -> "cleaning"
            TimelineCategory.Pet in categories -> "pet"
            else -> null
        },
    )

    fun entries(items: List<TimelineItem> = this.items): List<HistoryEntry> = items.map { item ->
        HistoryEntry(
            item = item,
            device = Household.devices.firstOrNull { it.id == item.deviceId },
            petColorIndex = item.pet?.let { ref -> Household.pets.indexOfFirst { it.id == ref.id }.takeIf { it >= 0 } },
        )
    }

    val visit: HistoryEntry get() = entries().first { it.item.id == 12L }

    /** What `GET /timeline/12` decodes for the camera box's visit. */
    val visitDetail = EventDetail(
        id = 12,
        deviceId = 1003,
        deviceType = "t5",
        ts = items.first { it.id == 12L }.ts,
        eventType = "10",
        eventKind = "toilet_visit",
        label = "Toilet visit",
        pet = items.first { it.id == 12L }.pet,
        fields = listOf(
            EventField("petWeight", "Pet weight", "4.31 kg"),
            EventField("shit_weight", "Waste weight", "35 g"),
            EventField("time_in", "Entered", "2026-11-15 04:02:00 +0100"),
            EventField("time_out", "Left", "2026-11-15 04:06:00 +0100"),
            EventField("petVoice", "Cat vocalised", "yes"),
            EventField("aesKey", "AES key", "…3f9a"),
            EventField("pos", "pos", "0"),
        ),
        media = cameraMedia,
    )

    fun state(
        entries: List<HistoryEntry> = entries(),
        paging: HistoryPaging = HistoryPaging(firstLoaded = true, endReached = true),
        query: HistoryQuery = HistoryQuery(Household.today),
    ) = HistoryUiState(
        today = Household.today,
        query = query,
        entries = entries,
        cacheLoaded = true,
        devices = Household.devices,
        pets = Household.pets,
        paging = paging,
        zone = Household.zone,
    )

    /** The morning, the day's first page read and more to come (no spinner: screenshots stay still). */
    val content = state(paging = HistoryPaging(firstLoaded = true))

    /** Pre-filtered on the camera box (from its page), under the toileting chip. */
    val filtered = state(
        entries = entries(items.filter { it.deviceId == 1003L && it.id == 12L }),
        query = HistoryQuery(Household.today, TimelineCategory.Toileting, deviceId = 1003),
    )

    val empty = state(entries = emptyList())

    val loading = state(entries = emptyList(), paging = HistoryPaging(loading = true))

    val failed = state(
        entries = emptyList(),
        paging = HistoryPaging(error = DomainError.Network()),
    )

    /** Cached cards shown while the server cannot be reached, the next page failed. */
    val offline = state(paging = HistoryPaging(firstLoaded = true, error = DomainError.Network()))

    val sheet = EventSheetState(visit, detail = visitDetail, loading = false)
}
