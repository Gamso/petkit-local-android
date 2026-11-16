package org.petkitlocal.core.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.domain.video.RecentClip
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of a camera's recent clips, in each of its states. */
@RunWith(RobolectricTestRunner::class)
// Wide enough for every clip of the row to be composed.
@Config(qualifiers = "w1000dp-h800dp")
class RecentClipsCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 15)
    private val calls = mutableListOf<String>()

    private val visit = clip(12, today, 4, 2, TimelineCategory.Toileting, pet = PetRef(1, "Mochi"))
    private val older = clip(8, today.minusDays(3), 7, 5, TimelineCategory.Toileting)
    private val yesterday = clip(9, today.minusDays(1), 22, 40, TimelineCategory.Pet, pet = PetRef(2, "Tigrou"))

    private fun show(state: RecentClipsState) {
        compose.setContent {
            PkTheme {
                RecentClipsCard(
                    state = state,
                    onOpenClip = { calls += "clip:${it.url}" },
                    onSeeAll = { calls += "all" },
                    onRetry = { calls += "retry" },
                )
            }
        }
    }

    private fun state(vararg clips: RecentClip, refreshed: Boolean = true, error: DomainError? = null) =
        RecentClipsState(
            clips = clips.toList(),
            cacheLoaded = true,
            refreshed = refreshed,
            error = error,
            zone = zone,
            today = today,
        )

    @Test
    fun `each clip says what happened and when, and opens its clip`() {
        show(state(visit, yesterday, older))

        compose.onNodeWithText("Recent videos").assertExists()
        compose.onNodeWithText("Mochi").assertExists()
        // The locale's own time format puts a narrow space before AM: matched up to it.
        compose.onNodeWithText("Today, 4:02", substring = true).assertExists()
        compose.onNodeWithText("Yesterday, 10:40", substring = true).assertExists()
        compose.onNodeWithText("Unknown pet").assertExists()
        compose.onNodeWithText("Nov 12, 7:05", substring = true).assertExists()

        compose.onNodeWithTag(RecentClipsTestTags.clip(12)).assertHasClickAction().performClick()
        compose.onNodeWithText("More videos in History").performClick()

        assertEquals(listOf("clip:${visit.clip.url}", "all"), calls)
    }

    @Test
    @Config(qualifiers = "fr-w1000dp-h800dp")
    fun `in French, days and times follow the locale`() {
        show(state(visit, yesterday, older))

        compose.onNodeWithText("Vidéos récentes").assertExists()
        compose.onNodeWithText("Aujourd’hui, 04:02").assertExists()
        compose.onNodeWithText("Hier, 22:40").assertExists()
        compose.onNodeWithText("Animal inconnu").assertExists()
        compose.onNodeWithText("12 nov., 07:05").assertExists()
    }

    @Test
    fun `before the server answers with nothing cached, the clips' room is kept`() {
        show(state(refreshed = false))

        compose.onNodeWithContentDescription("Loading").assertExists()
        compose.onNodeWithText("More videos in History").assertDoesNotExist()
    }

    @Test
    fun `a camera with no clip says so in one line, with no way to more`() {
        show(state())

        compose.onNodeWithText("This camera has recorded no video yet.").assertExists()
        compose.onNodeWithText("More videos in History").assertDoesNotExist()
    }

    @Test
    fun `with nothing cached and the server out of reach, it says why and retries`() {
        show(state(refreshed = false, error = DomainError.Network("down")))

        compose.onNodeWithText("The server cannot be reached.").assertExists()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `cached clips stay shown when a refresh fails`() {
        show(state(visit, refreshed = false, error = DomainError.Network("down")))

        compose.onNodeWithTag(RecentClipsTestTags.clip(12)).assertExists()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    private fun clip(
        id: Long,
        day: LocalDate,
        hour: Int,
        minute: Int,
        category: TimelineCategory,
        pet: PetRef? = null,
    ): RecentClip {
        val at = ZonedDateTime.of(day.atTime(hour, minute), zone).toInstant()
        val file = MediaFile(MediaKind.Highlight, "https://pets.example/app/v1/media/$id.mp4", null)
        val item = TimelineItem(
            id = id,
            kind = if (category == TimelineCategory.Toileting) "visit" else "event",
            categories = setOf(category),
            deviceId = 1003,
            deviceType = "t5",
            ts = at,
            displayTs = at,
            eventType = null,
            eventKind = if (category == TimelineCategory.Pet) "pet" else "toilet_visit",
            label = null,
            pet = pet,
            durationSec = null,
            weightG = null,
            facts = emptyList(),
            media = TimelineMedia(listOf(file)),
        )
        return RecentClip(item, file)
    }
}
