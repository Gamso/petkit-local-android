package org.petkitlocal.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.domain.model.DeviceFamily

/** The glyph of each device family: Material Symbols, except the litter box, which none fits. */
internal object HomeIcons {
    /**
     * A litter box: a hooded box seen from the front. Drawn for this project like PkIcons' own
     * line icons (2-unit round strokes on a 24-unit grid).
     */
    @Suppress("MagicNumber") // Path coordinates on the 24-unit icon grid.
    val LitterBox: ImageVector = ImageVector.Builder(
        name = "LitterBox",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4f, 20f)
        lineTo(4f, 12f)
        arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 12f)
        lineTo(20f, 20f)
        close()
        moveTo(8.5f, 20f)
        lineTo(8.5f, 15.5f)
        arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15.5f, y1 = 15.5f)
        lineTo(15.5f, 20f)
    }.build()

    fun forFamily(family: DeviceFamily): ImageVector = when (family) {
        DeviceFamily.LitterBox -> LitterBox
        DeviceFamily.Feeder -> PkIcons.Feed
        DeviceFamily.Fountain -> PkIcons.WaterDrop
        DeviceFamily.Purifier -> PkIcons.AirPurifier
        DeviceFamily.Other -> PkIcons.DeviceUnknown
    }
}
