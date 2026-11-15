package org.petkitlocal.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * WCAG 2.x contrast of every text colour on the backgrounds the components draw it on: at least
 * 4.5:1 (SPEC.md §6), in both palettes. A token change that breaks it fails here, not in review.
 */
class PkColorsContrastTest {
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("pairs")
    fun `text keeps 4,5 to 1`(palette: String, pair: String, foreground: Int, background: Int) {
        val ratio = contrast(Color(foreground), Color(background))
        assertTrue(ratio >= MIN_CONTRAST) { "$palette $pair is %.2f:1".format(ratio) }
    }

    companion object {
        private const val MIN_CONTRAST = 4.5

        private fun contrast(a: Color, b: Color): Double {
            val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (light + 0.05) / (dark + 0.05)
        }

        @JvmStatic
        fun pairs(): List<Arguments> = listOf("light" to LightPkColors, "dark" to DarkPkColors).flatMap { (name, c) ->
            listOf(
                "onSurface/surface" to (c.onSurface to c.surface),
                "onSurface/background" to (c.onSurface to c.background),
                "onSurface/surfaceMuted" to (c.onSurface to c.surfaceMuted),
                "onSurfaceMuted/surface" to (c.onSurfaceMuted to c.surface),
                "onSurfaceMuted/background" to (c.onSurfaceMuted to c.background),
                "onSurfaceMuted/surfaceMuted" to (c.onSurfaceMuted to c.surfaceMuted),
                "accent/surface" to (c.accent to c.surface),
                "accent/background" to (c.accent to c.background),
                "onAccent/accent" to (c.onAccent to c.accent),
                "onAccentContainer/accentContainer" to (c.onAccentContainer to c.accentContainer),
                "secondary/surface" to (c.secondary to c.surface),
                "onSecondary/secondary" to (c.onSecondary to c.secondary),
                "warning/surface" to (c.warning to c.surface),
                "onWarningContainer/warningContainer" to (c.onWarningContainer to c.warningContainer),
                "success/surface" to (c.success to c.surface),
                "danger/surface" to (c.danger to c.surface),
                "onDanger/danger" to (c.onDanger to c.danger),
            ).plus(c.pets.mapIndexed { i, pet -> "pet$i/surface" to (pet to c.surface) })
                .map { (pair, colors) -> Arguments.of(name, pair, colors.first.toArgb(), colors.second.toArgb()) }
        }
    }
}
