// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.feeder

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
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.core.ui.LiveCardState
import org.petkitlocal.core.ui.PreviewImages
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The feeder page in light/dark x EN/FR, the busiest state (the dual hopper, paused, offline,
 * asking for a portion) also at 130 %: a single hopper with its plan and a one-off feed, the dual
 * one, the weekly plan's editor with its problems, and the one-off feed form.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width, tall enough for the whole page. SDK 36: see LitterScreenshotTest.
@Config(qualifiers = "w360dp-h1900dp-xhdpi", sdk = [36])
class FeederScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun single() {
        if (variant.fontScale == 1f) capture("feeder_single") { Screen(FeederFixtures.single) }
    }

    /** Taller: a camera feeder's Today adds its meals eaten and their chart (#68). */
    @Test
    @Config(qualifiers = "w360dp-h2800dp-xhdpi", sdk = [36])
    fun dualPaused() = capture("feeder_dual_paused") {
        PreviewImages { Screen(FeederFixtures.dualPaused, DeviceFixtures.recentClips, DeviceFixtures.liveCard) }
    }

    @Test
    fun editor() {
        if (variant.fontScale != 1f) return
        capture("feeder_plan_editor") {
            PlanEditor(FeederFixtures.editor, saving = false, onEdit = {}, onSave = {}, onClose = {})
        }
    }

    @Test
    fun feedForm() {
        if (variant.fontScale != 1f) return
        capture("feeder_feed_form") {
            Box(Modifier.background(PkTheme.colors.background).padding(16.dp)) {
                FeedForm(
                    FeederFixtures.feedForm,
                    FeederFixtures.today,
                    saving = false,
                    onChange = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Composable
    private fun Screen(state: FeederUiState, clips: RecentClipsUi? = null, live: LiveCardState = LiveCardState()) =
        FeederScreen(state, DeviceNavigation(), FeederCallbacks(), clips = clips, live = live)

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
