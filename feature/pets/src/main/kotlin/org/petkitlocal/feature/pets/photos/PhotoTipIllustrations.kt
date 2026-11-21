package org.petkitlocal.feature.pets.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import org.petkitlocal.core.designsystem.theme.PkTheme

/** The examples of the tips screen, each a simple drawing of what the camera would see. */
enum class TipSketch {
    /** One pet, its head facing the camera, filling the frame, lit, sharp. */
    Good,
    TwoPets,

    /** The head turned away: a profile. */
    Side,

    /** The pet whole and small: the head is a few pixels. */
    Far,
    Dark,
    Blurry,
}

/**
 * One example as a drawing, not a photo (issue #98): a cat's head of simple shapes in a square
 * "viewfinder". Decorative: the label beside it says what it shows.
 */
@Composable
internal fun TipIllustration(sketch: TipSketch, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val backdrop = colors.surfaceMuted
    val fur = colors.pet(OCHRE)
    val ink = colors.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(PkTheme.shapes.tile),
    ) {
        drawRect(backdrop)
        val unit = size.minDimension
        when (sketch) {
            TipSketch.Good, TipSketch.Dark -> catHead(center, unit * GOOD_RADIUS, fur, ink)

            TipSketch.TwoPets -> {
                catHead(Offset(unit * 0.28f, unit * 0.55f), unit * PAIR_RADIUS, fur, ink)
                catHead(Offset(unit * 0.72f, unit * 0.5f), unit * PAIR_RADIUS, colors.pet(ROSE), ink)
            }

            TipSketch.Side -> catProfile(center, unit * GOOD_RADIUS, fur, ink)

            TipSketch.Far -> {
                val head = Offset(unit * 0.42f, unit * 0.52f)
                drawOval(fur, Offset(head.x, head.y), Size(unit * 0.3f, unit * 0.17f))
                catHead(head, unit * FAR_RADIUS, fur, ink)
            }

            TipSketch.Blurry -> BLUR_SHIFTS.forEach { (dx, dy) ->
                catHead(center + Offset(dx * unit, dy * unit), unit * GOOD_RADIUS, fur, ink, alpha = BLUR_ALPHA)
            }
        }
        if (sketch == TipSketch.Dark) drawRect(Color.Black.copy(alpha = DARK_ALPHA))
    }
}

/** A cat's head facing the camera, centred on [c], [r] its half-width. */
private fun DrawScope.catHead(c: Offset, r: Float, fur: Color, ink: Color, alpha: Float = 1f) {
    val furColor = fur.copy(alpha = alpha)
    val inkColor = ink.copy(alpha = alpha)
    listOf(-1f, 1f).forEach { side ->
        val ear = Path().apply {
            moveTo(c.x + side * r * 0.2f, c.y - r * 0.75f)
            lineTo(c.x + side * r * 0.78f, c.y - r * 1.3f)
            lineTo(c.x + side * r * 0.92f, c.y - r * 0.35f)
            close()
        }
        drawPath(ear, furColor)
    }
    drawOval(furColor, Offset(c.x - r, c.y - r * 0.9f), Size(r * 2, r * 1.8f))
    listOf(-1f, 1f).forEach { side ->
        drawCircle(inkColor, radius = r * 0.13f, center = Offset(c.x + side * r * 0.38f, c.y - r * 0.12f))
    }
    val nose = Path().apply {
        moveTo(c.x - r * 0.1f, c.y + r * 0.18f)
        lineTo(c.x + r * 0.1f, c.y + r * 0.18f)
        lineTo(c.x, c.y + r * 0.3f)
        close()
    }
    drawPath(nose, inkColor)
    val stroke = Stroke(width = r * 0.035f, cap = StrokeCap.Round)
    listOf(-1f, 1f).forEach { side ->
        listOf(0.22f, 0.36f).forEach { y ->
            drawLine(
                inkColor,
                Offset(c.x + side * r * 0.3f, c.y + r * y),
                Offset(c.x + side * r * 1.1f, c.y + r * (y + (y - 0.29f))),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** The same head turned to its right: one ear in front, one eye, the nose at the edge. */
private fun DrawScope.catProfile(c: Offset, r: Float, fur: Color, ink: Color) {
    listOf(0.05f, 0.5f).forEach { x ->
        val ear = Path().apply {
            moveTo(c.x + r * (x - 0.3f), c.y - r * 0.7f)
            lineTo(c.x + r * (x - 0.1f), c.y - r * 1.3f)
            lineTo(c.x + r * (x + 0.25f), c.y - r * 0.6f)
            close()
        }
        drawPath(ear, fur)
    }
    drawOval(fur, Offset(c.x - r * 0.9f, c.y - r * 0.85f), Size(r * 1.75f, r * 1.7f))
    // The muzzle, pointing away.
    drawOval(fur, Offset(c.x + r * 0.45f, c.y - r * 0.05f), Size(r * 0.7f, r * 0.5f))
    drawCircle(ink, radius = r * 0.12f, center = Offset(c.x + r * 0.45f, c.y - r * 0.15f))
    drawCircle(ink, radius = r * 0.08f, center = Offset(c.x + r * 1.1f, c.y + r * 0.12f))
}

private const val OCHRE = 3
private const val ROSE = 2
private const val GOOD_RADIUS = 0.33f
private const val PAIR_RADIUS = 0.17f
private const val FAR_RADIUS = 0.07f
private const val DARK_ALPHA = 0.72f
private const val BLUR_ALPHA = 0.28f

/** A sketch drawn several times a little apart: what a shaken photo looks like. */
private val BLUR_SHIFTS = listOf(0f to 0f, 0.025f to 0.01f, -0.02f to 0.015f, 0.012f to -0.02f, -0.01f to -0.012f)
