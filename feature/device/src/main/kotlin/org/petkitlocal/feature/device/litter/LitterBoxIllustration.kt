package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart

/**
 * Our own simple drawing of a self-cleaning litter box: a dome with its opening and the litter
 * inside, on a base holding the waste drawer (docs/DESIGN.md, Illustrations: no PetKit artwork).
 * It is its own composable so a photo of the user's box can replace it later; [litterAnchor]
 * says where each part is on it.
 */
@Composable
fun LitterBoxIllustration(modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Canvas(modifier) {
        val u = size.width / UNITS
        val stroke = Stroke(width = 1.6f * u)
        // Base and waste drawer.
        roundRect(colors.surface, 12f, 70f, 88f, 93f, 7f, u)
        roundRect(colors.onSurfaceMuted, 12f, 70f, 88f, 93f, 7f, u, stroke)
        roundRect(colors.surfaceMuted, 24f, 79f, 76f, 89f, 3f, u)
        roundRect(colors.onSurfaceMuted, 24f, 79f, 76f, 89f, 3f, u, stroke)
        drawLine(colors.onSurfaceMuted, Offset(43f * u, 84f * u), Offset(57f * u, 84f * u), strokeWidth = 2f * u)
        // The dome.
        roundRect(colors.surface, 14f, 12f, 86f, 76f, 34f, u)
        roundRect(colors.onSurfaceMuted, 14f, 12f, 86f, 76f, 34f, u, stroke)
        // Opening, and the litter in it.
        drawOval(colors.surfaceMuted, Offset(28f * u, 32f * u), Size(44f * u, 36f * u))
        drawArc(
            colors.secondary.copy(alpha = LITTER_ALPHA),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(28f * u, 32f * u),
            size = Size(44f * u, 36f * u),
        )
        drawOval(colors.onSurfaceMuted, Offset(28f * u, 32f * u), Size(44f * u, 36f * u), style = stroke)
        // Top panel and the deodorizer slot behind it.
        roundRect(colors.accent, 42f, 17f, 58f, 22f, 2.5f, u)
        roundRect(colors.onSurfaceMuted, 66f, 20f, 76f, 27f, 2f, u, stroke)
    }
}

/**
 * Where each part of [LitterBoxIllustration] is, and on which side its pill goes. A paired Pura
 * Air stands beside the box: its pill points at the box's side.
 */
@Suppress("MagicNumber") // Positions on the drawing.
fun litterAnchor(part: DevicePart): PartAnchor = when (part) {
    DevicePart.Litter -> PartAnchor(0.5f, 0.6f, PillSide.Left)
    DevicePart.WasteBin -> PartAnchor(0.3f, 0.84f, PillSide.Left)
    DevicePart.Bags -> PartAnchor(0.16f, 0.76f, PillSide.Left)
    DevicePart.Deodorizer -> PartAnchor(0.71f, 0.235f, PillSide.Right)
    DevicePart.Spray -> PartAnchor(0.84f, 0.45f, PillSide.Right)
    DevicePart.PuraAirSpray -> PartAnchor(0.86f, 0.55f, PillSide.Right)
    else -> PartAnchor(0.8f, 0.7f, PillSide.Right)
}

/** A rounded rectangle given in drawing units ([UNITS] across). */
@Suppress("LongParameterList") // The rectangle's four sides, its radius and the unit.
internal fun DrawScope.roundRect(
    color: Color,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    unit: Float,
    style: DrawStyle = Fill,
) = drawRoundRect(
    color = color,
    topLeft = Offset(left * unit, top * unit),
    size = Size((right - left) * unit, (bottom - top) * unit),
    cornerRadius = CornerRadius(radius * unit),
    style = style,
)

/** The drawings are made on a 100-unit square. */
internal const val UNITS = 100f
private const val LITTER_ALPHA = 0.35f
