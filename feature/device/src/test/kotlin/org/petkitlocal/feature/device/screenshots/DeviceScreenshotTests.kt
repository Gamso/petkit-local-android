// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.core.ui.RecentClipsCard
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.generic.GenericDeviceActions
import org.petkitlocal.feature.device.generic.GenericDeviceScreen
import org.petkitlocal.feature.device.settings.DeviceSettingsActions
import org.petkitlocal.feature.device.settings.DeviceSettingsScreen
import org.petkitlocal.feature.device.settings.SettingEditorContent
import org.petkitlocal.feature.device.settings.SettingEditorState
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The device screens in light/dark x EN/FR (docs/DESIGN.md), the busiest one at 130 % too. The
 * golden images are in src/test/screenshots; `./gradlew recordRoborazziDebug` re-records them.
 * SDK 36 for the same reason as ComponentScreenshotTest: Robolectric's SDK 37 graphics capture
 * some images blank.
 */

private fun capture(name: String, variant: ScreenshotVariant, content: @Composable () -> Unit) {
    captureRoboImage(
        filePath = "src/test/screenshots/${name}_$variant.png",
        roborazziComposeOptions = variant.options(),
    ) {
        PkTheme { content() }
    }
}

/** The generic screen, phone-sized: a fountain with its sheet and a banner, a purifier offline. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi", sdk = [36])
class GenericDeviceScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun fountain() = capture("generic_fountain", variant) {
        GenericDeviceScreen(
            DeviceFixtures.generic(commands = DeviceFixtures.sent),
            GenericDeviceActions(),
            clips = DeviceFixtures.recentClips,
        )
    }

    /** The sheet expanded over the page: everything behind More, destructive actions last. */
    @Test
    fun fountainExpanded() = capture("generic_fountain_expanded", variant) {
        GenericDeviceScreen(DeviceFixtures.generic(), GenericDeviceActions(), initiallyExpanded = true)
    }

    /** A litter box's long action names (French at 130 % is the hardest case) on whole words. */
    @Test
    fun litterBoxExpanded() = capture("generic_litter_expanded", variant) {
        GenericDeviceScreen(
            DeviceFixtures.generic(device = DeviceFixtures.litterBox),
            GenericDeviceActions(),
            initiallyExpanded = true,
        )
    }

    @Test
    fun purifierOffline() {
        if (variant.fontScale != 1f) return
        capture("generic_purifier_offline", variant) {
            GenericDeviceScreen(
                DeviceFixtures.generic(device = DeviceFixtures.purifier, offline = true),
                GenericDeviceActions(),
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}

/**
 * A camera's recent clips while the first refresh is on its way, when the server cannot be read
 * with nothing cached, and for a camera with no clip; the clips themselves are on the camera pages.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi", sdk = [36])
class RecentClipsScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun states() = capture("recent_clips_states", variant) {
        val base = DeviceFixtures.recentClips.state.copy(clips = emptyList(), refreshed = false)
        PkPreviewSurface {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    base.copy(refreshing = true),
                    base.copy(error = DomainError.Network("down")),
                    base.copy(refreshed = true),
                ).forEach { state -> RecentClipsCard(state, onOpenClip = {}, onSeeAll = {}, onRetry = {}) }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}

/** The settings screen, tall enough to show a litter box's categories down to the folded ones. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1700dp-xhdpi", sdk = [36])
class DeviceSettingsScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun settings() {
        if (variant.fontScale != 1f) return
        capture("settings_fountain", variant) {
            DeviceSettingsScreen(DeviceFixtures.settings(commands = DeviceFixtures.refused), DeviceSettingsActions())
        }
    }

    /** The Pura Max's settings by category, with what each does; the busiest one, so at 130 % too. */
    @Test
    fun litterBox() = capture("settings_litter", variant) {
        DeviceSettingsScreen(DeviceFixtures.settings(device = DeviceFixtures.litterSettings), DeviceSettingsActions())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}

/** Each editor's card, as its dialog shows it. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h760dp-xhdpi", sdk = [36])
class SettingEditorsScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun number() = editor(
        "editor_number",
        SettingEditorState(DeviceFixtures.fountainTime, problem = EntityWriteProblem.AboveMax(60.0)),
    )

    @Test
    fun select() = editor("editor_select", SettingEditorState(DeviceFixtures.flowMode))

    @Test
    fun time() = editor("editor_time", SettingEditorState(DeviceFixtures.refillTime))

    @Test
    fun text() = editor("editor_text", SettingEditorState(DeviceFixtures.quietHours, saving = true))

    private fun editor(name: String, state: SettingEditorState) = capture(name, variant) {
        PkPreviewSurface { SettingEditorContent(state, onSave = {}, onDismiss = {}) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.screens
    }
}
