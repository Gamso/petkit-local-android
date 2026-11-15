// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.core.testing

import android.content.res.Configuration
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.locale
import com.github.takahirom.roborazzi.uiMode

/**
 * One cell of the screenshot matrix of docs/DESIGN.md: light/dark x EN/FR x 100 %/130 % font
 * scale. Its [toString] names the image file. Screens use [screens] (light/dark x EN/FR, the
 * matrix of the M1 brief); components use [all].
 */
data class ScreenshotVariant(val dark: Boolean, val locale: String, val fontScale: Float = 1f) {
    fun options(): RoborazziComposeOptions = RoborazziComposeOptions {
        uiMode(if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        locale(locale)
        fontScale(fontScale)
    }

    override fun toString(): String = "${if (dark) "dark" else "light"}_${locale}_${(fontScale * PERCENT).toInt()}"

    companion object {
        private const val PERCENT = 100

        val all: List<ScreenshotVariant> = listOf(false, true).flatMap { dark ->
            listOf("en", "fr").flatMap { locale -> listOf(1f, 1.3f).map { ScreenshotVariant(dark, locale, it) } }
        }

        val screens: List<ScreenshotVariant> = all.filter { it.fontScale == 1f }
    }
}
