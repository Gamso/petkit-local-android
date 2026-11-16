package org.petkitlocal.feature.history

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.robolectric.RobolectricTestRunner

/**
 * The clip player opened from outside History (a camera's recent clips): its destination names the
 * clip, and the screen is History's own player, whose way out goes back.
 */
@RunWith(RobolectricTestRunner::class)
class ClipRouteTest {
    @get:Rule
    val compose = createComposeRule()

    private val url = "https://pets.example/app/v1/media/1003/clip.mp4"

    private fun viewModel(kind: String?) = ClipViewModel(
        SavedStateHandle(mapOf(CLIP_URL_KEY to url, CLIP_KIND_KEY to kind)),
        ClipPlayerFactory { error("no player in these tests") },
    )

    @Test
    fun `the destination names the clip, and a kind this app does not know is still a clip`() {
        assertEquals(MediaFile(MediaKind.Playback, url, null), viewModel("Playback").clip)
        assertEquals(MediaFile(MediaKind.Highlight, url, null), viewModel("Hologram").clip)
        // A photo's kind cannot be played: the player titles it as a clip.
        assertEquals(MediaKind.Highlight, viewModel("Snapshot").clip.kind)
    }

    @Test
    fun `the screen is the clip player, titled by its kind, and closing it goes back`() {
        var back = 0
        val clip = viewModel("Timelapse").clip
        compose.setContent { PkTheme { ClipScreen(clip, playerFactory = null, onClose = { back++ }) } }

        compose.onNodeWithTag(HistoryTestTags.CLIP_PLAYER).assertExists()
        compose.onNodeWithText("Timelapse").assertExists()
        compose.onNodeWithContentDescription("Close").performClick()

        assertEquals(1, back)
    }
}
