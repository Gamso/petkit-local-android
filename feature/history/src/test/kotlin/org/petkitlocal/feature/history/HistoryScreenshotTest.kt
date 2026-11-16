// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History in light/dark x EN/FR, the busiest state also at 130 % font scale: the morning's cards
 * with more on the way, one device under a chip, an empty day, the server unreachable with
 * nothing cached, and a visit's detail sheet. The golden images are in src/test/screenshots.
 * Thumbnails are not fetched here: their placeholder symbol stands in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width. SDK 36: Robolectric's SDK 37 graphics capture some images blank.
@Config(qualifiers = "w360dp-h1400dp-xhdpi", sdk = [36])
class HistoryScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun content() = capture("history_content") { HistoryScreen(HistoryFixtures.content, HistoryCallbacks()) }

    @Test
    fun filtered() = screen("history_filtered") { HistoryScreen(HistoryFixtures.filtered, HistoryCallbacks()) }

    @Test
    fun empty() = screen("history_empty") { HistoryScreen(HistoryFixtures.empty, HistoryCallbacks()) }

    @Test
    fun failed() = screen("history_failed") { HistoryScreen(HistoryFixtures.failed, HistoryCallbacks()) }

    @Test
    fun sheet() = capture("history_sheet") {
        // The sheet's own surface, below where its drag handle sits.
        Box(Modifier.background(PkTheme.colors.surface).padding(top = 24.dp)) {
            EventSheetContent(HistoryFixtures.sheet, Household.zone, HistoryCallbacks())
        }
    }

    /** The other states at 100 % only: the busiest one carries the 130 % check. */
    private fun screen(name: String, content: @Composable () -> Unit) {
        if (variant.fontScale == 1f) capture(name, content)
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { content() }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}
