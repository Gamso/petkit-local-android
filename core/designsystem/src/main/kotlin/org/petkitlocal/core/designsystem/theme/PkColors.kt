package org.petkitlocal.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of docs/DESIGN.md. Screens read these through [PkTheme.colors], never a raw
 * value. Every text/background pair used by the components keeps a contrast of at least 4.5:1 in
 * both palettes; `PkColorsContrastTest` checks it.
 */
@Immutable
data class PkColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val success: Color,
    val danger: Color,
    val onDanger: Color,
    val outline: Color,
    /** Hero gradient tints per device family, each fading into [background]. */
    val heroLitter: Color,
    val heroFeeder: Color,
    val heroFountain: Color,
    val heroPet: Color,
    /**
     * What a product photo is laid on in this palette ([org.petkitlocal.core.designsystem.photo.PkDevicePhoto]):
     * none in light, where its white background melts into the page by a multiply blend; a light
     * plate in dark, where the same blend would sink a white product into the dark page.
     */
    val photoPlate: Color,
    /** Pet colours in their fixed order, so charts and avatars agree everywhere. */
    val pets: List<Color>,
    /** A Dual-Hopper's hoppers, 1 then 2: the food drawn in each and the portions it gave. */
    val hoppers: List<Color>,
    val isDark: Boolean,
) {
    /** The colour of the pet at [index] in the household's order, cycling past the last. */
    fun pet(index: Int): Color = pets[Math.floorMod(index, pets.size)]

    /** The colour of hopper [number] (1 or 2). */
    fun hopper(number: Int): Color = hoppers[Math.floorMod(number - 1, hoppers.size)]
}

val LightPkColors = PkColors(
    background = Color(0xFFEEF0F3),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF1F3F6),
    onSurface = Color(0xFF1B1F24),
    onSurfaceMuted = Color(0xFF5B6470),
    accent = Color(0xFF0B7A72),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE6F4F2),
    onAccentContainer = Color(0xFF075E58),
    secondary = Color(0xFF2E4C78),
    onSecondary = Color(0xFFFFFFFF),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFFF4E5),
    onWarningContainer = Color(0xFF7A3A06),
    success = Color(0xFF1F7A3A),
    danger = Color(0xFFB42318),
    onDanger = Color(0xFFFFFFFF),
    outline = Color(0xFFD5D9DF),
    heroLitter = Color(0xFFE3F1EF),
    heroFeeder = Color(0xFFE8EEF7),
    heroFountain = Color(0xFFE2EFF7),
    heroPet = Color(0xFFF6EBDF),
    photoPlate = Color.Unspecified,
    pets = listOf(Color(0xFF0B7A72), Color(0xFF2E4C78), Color(0xFF7B4FA8), Color(0xFFA8436A)),
    hoppers = listOf(Color(0xFF0B7A72), Color(0xFFB45309)),
    isDark = false,
)

val DarkPkColors = PkColors(
    background = Color(0xFF101316),
    surface = Color(0xFF1A1E23),
    surfaceMuted = Color(0xFF252B32),
    onSurface = Color(0xFFE6E9ED),
    onSurfaceMuted = Color(0xFFA4ADB9),
    accent = Color(0xFF5CC8BD),
    onAccent = Color(0xFF00211E),
    accentContainer = Color(0xFF123A36),
    onAccentContainer = Color(0xFFA6E6DE),
    secondary = Color(0xFF9DB8E3),
    onSecondary = Color(0xFF0D1B30),
    warning = Color(0xFFF2A65A),
    warningContainer = Color(0xFF3A2810),
    onWarningContainer = Color(0xFFFFD8A8),
    success = Color(0xFF74CF8F),
    danger = Color(0xFFFF8B7E),
    onDanger = Color(0xFF3A0904),
    outline = Color(0xFF3A424C),
    heroLitter = Color(0xFF16302D),
    heroFeeder = Color(0xFF1B2433),
    heroFountain = Color(0xFF15293A),
    heroPet = Color(0xFF33281C),
    photoPlate = Color(0xFFDDE2E7),
    pets = listOf(Color(0xFF5CC8BD), Color(0xFF9DB8E3), Color(0xFFC8A2EC), Color(0xFFF291B3)),
    hoppers = listOf(Color(0xFF5CC8BD), Color(0xFFF2A65A)),
    isDark = true,
)
