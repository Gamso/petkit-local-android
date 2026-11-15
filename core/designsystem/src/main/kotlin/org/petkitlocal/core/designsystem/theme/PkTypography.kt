package org.petkitlocal.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.petkitlocal.core.designsystem.R

/*
 * Both families are the variable fonts from Google Fonts (SIL OFL, core/designsystem/licenses),
 * bundled: no font is ever fetched over the network. Each weight is an instance of the one file,
 * selected through its `wght` axis.
 */

private fun variableFamily(resource: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { weight ->
        Font(resource, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

/** Plus Jakarta Sans: the display and headline face. */
val PlusJakartaSans = variableFamily(R.font.plus_jakarta_sans, FontWeight.Bold, FontWeight.ExtraBold)

/** Inter: everything else. */
val Inter = variableFamily(
    R.font.inter,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

/** The type scale of docs/DESIGN.md. Sizes are `sp`, so they follow the user's font scale. */
@Immutable
data class PkTypography(
    /** Home title. */
    val display: TextStyle,
    /** Big daily figures. */
    val headlineLarge: TextStyle,
    /** Section titles. */
    val headlineSmall: TextStyle,
    /** Page titles. */
    val titleLarge: TextStyle,
    /** Card titles. */
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    /** Buttons and chips. */
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

val DefaultPkTypography = PkTypography(
    display = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
