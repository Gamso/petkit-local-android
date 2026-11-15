package org.petkitlocal.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's own line icons: 24 x 24, 2-unit strokes with round ends, tinted by whoever draws
 * them. Drawn for this project (docs/DESIGN.md: no PetKit artwork), and kept to the few the base
 * components need; feature icons join them as screens arrive.
 */
@Suppress("MagicNumber") // Path coordinates on the 24-unit icon grid.
object PkIcons {
    val Check: ImageVector = icon("Check") {
        moveTo(5f, 12.5f)
        lineTo(10f, 17.5f)
        lineTo(19f, 7f)
    }

    val ChevronRight: ImageVector = icon("ChevronRight") {
        moveTo(9f, 5f)
        lineTo(16f, 12f)
        lineTo(9f, 19f)
    }

    val Refresh: ImageVector = icon("Refresh") {
        moveTo(19.5f, 12f)
        arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17.3f, y1 = 6.7f)
        moveTo(17.5f, 2.5f)
        lineTo(17.5f, 7f)
        lineTo(13f, 7f)
    }

    val Warning: ImageVector = icon("Warning") {
        moveTo(12f, 3.5f)
        lineTo(21.5f, 20f)
        lineTo(2.5f, 20f)
        close()
        moveTo(12f, 9.5f)
        lineTo(12f, 14f)
        moveTo(12f, 17f)
        lineTo(12f, 17.2f)
    }

    val Offline: ImageVector = icon("Offline") {
        moveTo(7f, 18f)
        arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7.5f, y1 = 10f)
        arcTo(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17.8f, y1 = 11.2f)
        arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17f, y1 = 18f)
        close()
        moveTo(3.5f, 3.5f)
        lineTo(20.5f, 20.5f)
    }

    val Clean: ImageVector = icon("Clean") {
        moveTo(12f, 3f)
        lineTo(13.8f, 10.2f)
        lineTo(21f, 12f)
        lineTo(13.8f, 13.8f)
        lineTo(12f, 21f)
        lineTo(10.2f, 13.8f)
        lineTo(3f, 12f)
        lineTo(10.2f, 10.2f)
        close()
    }

    val Level: ImageVector = icon("Level") {
        moveTo(4f, 15f)
        quadTo(8f, 11f, 12f, 15f)
        quadTo(16f, 19f, 20f, 15f)
        moveTo(4f, 20f)
        lineTo(20f, 20f)
        moveTo(12f, 3f)
        lineTo(12f, 10f)
        moveTo(9f, 7f)
        lineTo(12f, 10f)
        lineTo(15f, 7f)
    }

    val Deodorize: ImageVector = icon("Deodorize") {
        moveTo(8f, 21f)
        lineTo(8f, 11f)
        lineTo(14f, 11f)
        lineTo(14f, 21f)
        close()
        moveTo(9.5f, 11f)
        lineTo(9.5f, 7f)
        lineTo(12.5f, 7f)
        lineTo(12.5f, 11f)
        moveTo(17f, 5f)
        lineTo(17.2f, 5f)
        moveTo(20f, 8f)
        lineTo(20.2f, 8f)
        moveTo(17f, 9.5f)
        lineTo(17.2f, 9.5f)
    }

    val Feed: ImageVector = icon("Feed") {
        moveTo(3f, 12f)
        lineTo(21f, 12f)
        arcTo(9f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 3f, y1 = 12f)
        close()
        moveTo(8f, 9f)
        lineTo(8.2f, 9f)
        moveTo(12f, 7.5f)
        lineTo(12.2f, 7.5f)
        moveTo(16f, 9f)
        lineTo(16.2f, 9f)
    }

    val Clock: ImageVector = icon("Clock") {
        moveTo(21f, 12f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 11.99f)
        moveTo(12f, 7f)
        lineTo(12f, 12f)
        lineTo(15.5f, 14f)
    }

    val Calendar: ImageVector = icon("Calendar") {
        moveTo(4f, 6f)
        lineTo(20f, 6f)
        lineTo(20f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(4f, 10f)
        lineTo(20f, 10f)
        moveTo(8f, 3.5f)
        lineTo(8f, 7.5f)
        moveTo(16f, 3.5f)
        lineTo(16f, 7.5f)
    }

    val History: ImageVector = icon("History") {
        moveTo(9f, 6f)
        lineTo(20f, 6f)
        moveTo(9f, 12f)
        lineTo(20f, 12f)
        moveTo(9f, 18f)
        lineTo(20f, 18f)
        moveTo(4.5f, 6f)
        lineTo(4.7f, 6f)
        moveTo(4.5f, 12f)
        lineTo(4.7f, 12f)
        moveTo(4.5f, 18f)
        lineTo(4.7f, 18f)
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
        .build()
}
