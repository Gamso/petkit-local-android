package org.petkitlocal.feature.device.fountain

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import org.petkitlocal.core.designsystem.theme.PkColors
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.feature.device.litter.PartAnchor
import org.petkitlocal.feature.device.litter.PillSide
import org.petkitlocal.feature.device.litter.UNITS
import org.petkitlocal.feature.device.litter.roundRect

/**
 * Our own simple drawing of a drinking fountain: a body on a narrower foot, a wide dish of water
 * on top with the spout's water falling into it, and the filter seen through a window (docs/
 * DESIGN.md, Illustrations: no PetKit artwork). One drawing for every model, in its own composable
 * so a model's drawing or a photo of the user's fountain can replace it later; [fountainAnchor]
 * says where each part is on it.
 */
@Composable
fun FountainIllustration(modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Canvas(modifier) {
        val u = size.width / UNITS
        // Drawn on 8 to 92 across and 22 to 94 down, then enlarged to fill the square.
        withTransform({
            translate(top = -SHIFT * u)
            scale(SCALE, SCALE, pivot = Offset(PIVOT_X * u, PIVOT_Y * u))
        }) { drawFountain(u, colors) }
    }
}

@Suppress("MagicNumber") // Positions on the drawing.
private fun DrawScope.drawFountain(u: Float, colors: PkColors) {
    val stroke = Stroke(width = 1.6f * u)
    // Foot and body.
    roundRect(colors.surface, 26f, 84f, 74f, 94f, 4f, u)
    roundRect(colors.onSurfaceMuted, 26f, 84f, 74f, 94f, 4f, u, stroke)
    roundRect(colors.surface, 18f, 50f, 82f, 88f, 12f, u)
    roundRect(colors.onSurfaceMuted, 18f, 50f, 82f, 88f, 12f, u, stroke)
    // The filter behind its window, and the pump's light under it.
    roundRect(colors.surfaceMuted, 40f, 60f, 60f, 76f, 3f, u)
    listOf(45f, 50f, 55f).forEach { x ->
        drawLine(colors.onSurfaceMuted, Offset(x * u, 63f * u), Offset(x * u, 73f * u), strokeWidth = 1.2f * u)
    }
    roundRect(colors.onSurfaceMuted, 40f, 60f, 60f, 76f, 3f, u, stroke)
    drawCircle(colors.accent, radius = 2.2f * u, center = Offset(50f * u, 81.5f * u))
    // The dish, and the water in it.
    drawOval(colors.surface, Offset(8f * u, 38f * u), Size(84f * u, 22f * u))
    drawOval(colors.accent.copy(alpha = WATER_ALPHA), Offset(15f * u, 41.5f * u), Size(70f * u, 15f * u))
    drawOval(colors.onSurfaceMuted, Offset(8f * u, 38f * u), Size(84f * u, 22f * u), style = stroke)
    // The spout, and its water falling into the dish.
    val spout = Offset(41f * u, 32f * u)
    val spoutSize = Size(18f * u, 16f * u)
    drawArc(colors.surface, 180f, 180f, useCenter = true, topLeft = spout, size = spoutSize)
    drawArc(colors.onSurfaceMuted, 180f, 180f, useCenter = false, topLeft = spout, size = spoutSize, style = stroke)
    val water = Stroke(width = 1.8f * u, cap = StrokeCap.Round)
    listOf(-1f, 1f).forEach { side ->
        val fall = Path().apply {
            moveTo(50f * u, 31f * u)
            quadraticTo((50f + side * 14f) * u, 24f * u, (50f + side * 20f) * u, 46f * u)
        }
        drawPath(fall, colors.accent, style = water)
    }
    drawLine(colors.accent, Offset(50f * u, 22f * u), Offset(50f * u, 31f * u), 1.8f * u, cap = StrokeCap.Round)
}

/** Where each part of [FountainIllustration] is, and on which side its pill goes. */
@Suppress("MagicNumber") // Positions on the drawing.
fun fountainAnchor(part: DevicePart): PartAnchor = when (part) {
    DevicePart.Water -> anchor(36f, 49f, PillSide.Left)
    DevicePart.Tray -> anchor(14f, 49f, PillSide.Left)
    DevicePart.Filter -> anchor(60f, 68f, PillSide.Right)
    DevicePart.Pump -> anchor(50f, 81.5f, PillSide.Left)
    DevicePart.CleanTank -> anchor(82f, 62f, PillSide.Right)
    DevicePart.WasteTank -> anchor(70f, 89f, PillSide.Right)
    DevicePart.Battery -> anchor(72f, 89f, PillSide.Right)
    else -> anchor(80f, 72f, PillSide.Right)
}

/** A point in drawing units, where the enlarged drawing puts it, as fractions of its size. */
private fun anchor(x: Float, y: Float, side: PillSide) = PartAnchor(
    ((x - PIVOT_X) * SCALE + PIVOT_X) / UNITS,
    ((y - PIVOT_Y) * SCALE + PIVOT_Y - SHIFT) / UNITS,
    side,
)

private const val SCALE = 1.15f
private const val PIVOT_X = 50f
private const val PIVOT_Y = 58f
private const val SHIFT = 8f
private const val WATER_ALPHA = 0.3f
