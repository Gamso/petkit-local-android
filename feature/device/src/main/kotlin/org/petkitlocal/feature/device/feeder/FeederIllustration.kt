package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.feature.device.litter.PartAnchor
import org.petkitlocal.feature.device.litter.PillSide
import org.petkitlocal.feature.device.litter.UNITS
import org.petkitlocal.feature.device.litter.roundRect

/**
 * Our own simple drawing of an automatic feeder: a tall body with a lid, the hopper's window with
 * its food (split in two on a Dual-Hopper, each hopper in its colour), the outlet and the bowl
 * (docs/DESIGN.md, Illustrations: no PetKit artwork). Its own composable, so a photo can replace
 * it later; [feederAnchor] says where each part is on it.
 */
@Composable
fun FeederIllustration(dualHopper: Boolean, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Canvas(modifier) {
        val u = size.width / UNITS
        val stroke = Stroke(width = 1.6f * u)
        // Body and lid.
        roundRect(colors.surface, 26f, 10f, 74f, 92f, 14f, u)
        roundRect(colors.onSurfaceMuted, 26f, 10f, 74f, 92f, 14f, u, stroke)
        roundRect(colors.surfaceMuted, 23f, 5f, 77f, 17f, 6f, u)
        roundRect(colors.onSurfaceMuted, 23f, 5f, 77f, 17f, 6f, u, stroke)
        // The hopper's window and the food in it.
        roundRect(colors.surfaceMuted, 34f, 22f, 66f, 58f, 6f, u)
        if (dualHopper) {
            // Each hopper's food in its own colour, which its portions are written in on the page.
            clipRect(right = 50f * u) {
                roundRect(colors.hopper(1).copy(alpha = FOOD_ALPHA), 34f, 38f, 66f, 58f, 6f, u)
            }
            clipRect(left = 50f * u) {
                roundRect(colors.hopper(2).copy(alpha = FOOD_ALPHA), 34f, 38f, 66f, 58f, 6f, u)
            }
        } else {
            roundRect(colors.secondary.copy(alpha = FOOD_ALPHA), 34f, 38f, 66f, 58f, 6f, u)
        }
        roundRect(colors.onSurfaceMuted, 34f, 22f, 66f, 58f, 6f, u, stroke)
        if (dualHopper) {
            drawLine(colors.onSurfaceMuted, Offset(50f * u, 22f * u), Offset(50f * u, 58f * u), strokeWidth = 1.6f * u)
        }
        // Outlet, and the bowl in front.
        roundRect(colors.onSurfaceMuted, 44f, 62f, 56f, 68f, 2f, u)
        drawOval(colors.surface, Offset(28f * u, 74f * u), Size(44f * u, 16f * u))
        drawOval(colors.secondary.copy(alpha = FOOD_ALPHA), Offset(36f * u, 77f * u), Size(28f * u, 7f * u))
        drawOval(colors.onSurfaceMuted, Offset(28f * u, 74f * u), Size(44f * u, 16f * u), style = stroke)
        // The panel's light.
        roundRect(colors.accent, 45f, 9f, 55f, 12.5f, 1.75f, u)
    }
}

/** Where each part of [FeederIllustration] is, and on which side its pill goes. */
@Suppress("MagicNumber") // Positions on the drawing.
fun feederAnchor(part: DevicePart): PartAnchor = when (part) {
    DevicePart.Food -> PartAnchor(0.5f, 0.48f, PillSide.Left)
    DevicePart.Hopper1 -> PartAnchor(0.42f, 0.48f, PillSide.Left)
    DevicePart.Hopper2 -> PartAnchor(0.58f, 0.48f, PillSide.Right)
    DevicePart.Bowl -> PartAnchor(0.5f, 0.81f, PillSide.Left)
    DevicePart.Desiccant -> PartAnchor(0.66f, 0.11f, PillSide.Right)
    else -> PartAnchor(0.7f, 0.72f, PillSide.Right)
}

private const val FOOD_ALPHA = 0.35f
