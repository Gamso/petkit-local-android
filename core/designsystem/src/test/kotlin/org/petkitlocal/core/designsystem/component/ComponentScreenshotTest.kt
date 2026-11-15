// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.uiMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every base component in light/dark x EN/FR x 100 %/130 % font scale (docs/DESIGN.md). The
 * golden images are in src/test/screenshots; `./gradlew check` fails on any pixel change, and
 * `./gradlew recordRoborazziDebug` records new ones after an intended change.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A phone-width window, tall enough for every sample; the image is cropped to the content.
// SDK 36, not the target 37: Robolectric 4.18's SDK 37 native graphics captures a blank image
// for 5 to 14 of the 48 variants of a run, in no fixed order (Windows and Linux alike), while
// SDK 36 renders all of them every time. The Compose UI tests do run on SDK 37. Move this to
// the target SDK when a Robolectric release renders it reliably.
@Config(qualifiers = "w360dp-h1000dp-xhdpi", sdk = [36])
class ComponentScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun buttons() = capture("buttons") { PkButtonSamples() }

    @Test
    fun cards() = capture("cards") { PkCardSamples() }

    @Test
    fun chips() = capture("chips") { PkChipSamples() }

    @Test
    fun stateViews() = capture("state_views") { PkStateViewSamples() }

    @Test
    fun actionSheet() = capture("action_sheet") { PkActionSheetSamples() }

    @Test
    fun confirmDialog() = capture("confirm_dialog") { PkConfirmDialogSamples() }

    private fun capture(component: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${component}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkPreviewSurface(content)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}

/** One cell of the screenshot matrix. Its [toString] names the image file. */
data class ScreenshotVariant(val dark: Boolean, val locale: String, val fontScale: Float) {
    fun options(): RoborazziComposeOptions = RoborazziComposeOptions {
        uiMode(if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        locale(locale)
        fontScale(fontScale)
    }

    override fun toString(): String = "${if (dark) "dark" else "light"}_${locale}_${(fontScale * PERCENT).toInt()}"

    companion object {
        private const val PERCENT = 100
        val all: List<ScreenshotVariant> = listOf(false, true).flatMap { dark ->
            listOf("en", "fr").flatMap { locale ->
                listOf(1f, 1.3f).map { scale -> ScreenshotVariant(dark, locale, scale) }
            }
        }
    }
}
