// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.device.quickactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.device.QuickActionEditResult
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.core.ui.PreviewImages
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.LocalQuickActions
import org.petkitlocal.feature.device.common.QuickActions
import org.petkitlocal.feature.device.generic.GenericDeviceActions
import org.petkitlocal.feature.device.generic.GenericDeviceScreen
import org.petkitlocal.feature.device.litter.LitterCallbacks
import org.petkitlocal.feature.device.litter.LitterFixtures
import org.petkitlocal.feature.device.litter.LitterScreen
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The quick actions (issue #40) in light/dark x EN/FR: the editor (the busiest state, a litter
 * box's chosen row with a destructive action and the full-row notice, at 130 % too; the app's
 * row on a fountain), the litter box page with the user's row, and the expanded sheet's way to
 * the editor. The golden images are in src/test/screenshots. SDK 36: Robolectric's SDK 37
 * graphics capture some images blank (ComponentScreenshotTest).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1500dp-xhdpi", sdk = [36])
class QuickActionsScreenshotTest(private val variant: ScreenshotVariant) {
    private fun editorOf(device: Device, pinned: List<String>?, notice: QuickActionEditResult? = null) =
        DeviceActionOrder.layout(device.actions, pinned).let { layout ->
            QuickActionsEditorState(
                device.modelName,
                layout.main,
                layout.more,
                custom = pinned != null,
                notice = notice,
            )
        }

    /** A Pura Max's chosen row, a destructive action in it, after an add found it full. */
    @Test
    fun editorChosen() = capture("quick_actions_editor") {
        QuickActionsEditor(
            editorOf(
                DeviceFixtures.litterBox,
                listOf("dump_litter", "cleaning_start", "level_litter"),
                QuickActionEditResult.RowFull,
            ),
            QuickActionsCallbacks(),
        )
    }

    @Test
    fun editorByApp() {
        if (variant.fontScale == 1f) {
            capture("quick_actions_editor_default") {
                QuickActionsEditor(editorOf(DeviceFixtures.fountain, pinned = null), QuickActionsCallbacks())
            }
        }
    }

    /** The litter box page's own scaffold with the user's row: Dump Litter pinned first. */
    @Test
    fun litterPinned() {
        if (variant.fontScale != 1f) return
        capture("quick_actions_litter") {
            CompositionLocalProvider(
                LocalQuickActions provides QuickActions(pinned = listOf("dump_litter", "level_litter"), onEdit = {}),
            ) {
                PreviewImages { LitterScreen(LitterFixtures.content, DeviceNavigation(), LitterCallbacks()) }
            }
        }
    }

    /** The expanded sheet ends with the way to the editor. */
    @Test
    fun sheetEditEntry() {
        if (variant.fontScale != 1f) return
        capture("quick_actions_sheet") {
            CompositionLocalProvider(LocalQuickActions provides QuickActions(onEdit = {})) {
                GenericDeviceScreen(DeviceFixtures.generic(), GenericDeviceActions(), initiallyExpanded = true)
            }
        }
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
