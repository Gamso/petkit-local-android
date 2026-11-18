package org.petkitlocal.core.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.petkitlocal.core.domain.video.LatestStill
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the live card at the top of a camera's page. */
@RunWith(RobolectricTestRunner::class)
class LiveCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 15)
    private var opened = 0

    private fun show(still: LatestStill?) {
        compose.setContent {
            PreviewImages {
                PkTheme {
                    LiveCard(
                        deviceName = "Purobot Ultra",
                        state = LiveCardState(still, zone, today),
                        onOpen = { opened++ },
                    )
                }
            }
        }
    }

    private fun still(day: LocalDate, hour: Int, minute: Int) = LatestStill(
        "https://pets.example/app/v1/media/thumb/a/snap.jpg",
        ZonedDateTime.of(day.atTime(hour, minute), zone).toInstant(),
    )

    private val card get() = compose.onNodeWithTag(LiveCardTestTags.CARD)

    @Test
    fun `with a still, one button names the device and when the still was taken, and opens the live video`() {
        show(still(today, 4, 2))

        // The locale's own time format puts a narrow space before AM.
        card.assertContentDescriptionEquals("Watch Purobot Ultra live. Still from 4:02 AM")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .performClick()
        // Its words are the description's: TalkBack reads nothing twice.
        compose.onNodeWithText("Watch live").assertDoesNotExist()

        assertEquals(1, opened)
    }

    @Test
    fun `a still from another day says which`() {
        show(still(today.minusDays(1), 22, 40))
        card.assertContentDescriptionEquals("Watch Purobot Ultra live. Still from yesterday, 10:40 PM")
    }

    @Test
    fun `a still from an older day says the day without its year`() {
        show(still(today.minusDays(3), 7, 5))
        card.assertContentDescriptionEquals("Watch Purobot Ultra live. Still from Nov 12, 7:05 AM")
    }

    @Test
    fun `without a still, the card still opens the live video, and says no time`() {
        show(still = null)

        card.assertContentDescriptionEquals("Watch Purobot Ultra live").performClick()

        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `in French, the still's day and time follow the locale`() {
        show(still(today, 17, 0))
        card.assertContentDescriptionEquals("Voir Purobot Ultra en direct. Image de 17:00")
    }
}
