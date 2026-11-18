package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R

/**
 * Which side of the drawing a part's pill goes. The app's languages read left to right; the hero
 * is a picture, and is not mirrored.
 */
enum class PillSide { Left, Right }

/** Where a part is on a family's drawing, as fractions of its width and height, and its pill's side. */
data class PartAnchor(val x: Float, val y: Float, val side: PillSide)

/**
 * The hero of a device page (docs/DESIGN.md, DeviceHero). With the model's [photo], the photo and
 * its pills ([PhotoHero]). Without one, the family's drawing in the middle and one [HotspotPill]
 * per hotspot on either side, each joined by a line to a dot on its part. A pill goes as near its
 * part's height as the others leave room for; the hero grows to fit them.
 */
@Composable
internal fun DeviceHero(
    hotspots: List<Hotspot>,
    anchors: (DevicePart) -> PartAnchor,
    onHotspot: (DevicePart) -> Unit,
    modifier: Modifier = Modifier,
    photo: DevicePhoto? = null,
    illustration: @Composable () -> Unit,
) {
    if (photo != null) {
        PhotoHero(hotspots, photo, onHotspot, modifier)
        return
    }
    val colors = PkTheme.colors
    val labels = rememberServerLabels()
    val links = remember { HeroLinks() }
    val lineColor = colors.onSurfaceMuted
    Layout(
        content = {
            Box { illustration() }
            hotspots.forEach { hotspot ->
                HotspotPill(
                    label = hotspotLabel(hotspot, labels),
                    value = readingText(hotspot.reading, labels, short = true),
                    attention = hotspot.attention,
                    onClick = { onHotspot(hotspot.part) },
                )
            }
        },
        modifier = modifier.drawWithContent {
            links.lines.forEach { link ->
                drawLine(lineColor.copy(alpha = LINE_ALPHA), link.from, link.to, strokeWidth = 1.dp.toPx())
            }
            drawContent()
            links.lines.forEach { link ->
                drawCircle(colors.surface, radius = DOT_RADIUS.toPx() + 2.dp.toPx(), center = link.to)
                drawCircle(if (link.attention) colors.warning else colors.accent, DOT_RADIUS.toPx(), link.to)
            }
        },
    ) { measurables, constraints ->
        placeHero(measurables, constraints, hotspots.map { anchors(it.part) to it.attention }, links)
    }
}

/**
 * Measures and places the drawing ([measurables] first) and the pills, [parts] giving each pill's
 * anchor and whether it needs attention, and leaves the lines to draw in [links].
 */
private fun MeasureScope.placeHero(
    measurables: List<Measurable>,
    constraints: Constraints,
    parts: List<Pair<PartAnchor, Boolean>>,
    links: HeroLinks,
): MeasureResult {
    val width = constraints.maxWidth
    val gap = GAP.roundToPx()
    // The pills keep a readable width; the drawing gives way first, down to MIN_DRAWING.
    val drawing = min((width * DRAWING_SHARE).roundToInt(), MAX_DRAWING.roundToPx())
        .coerceAtMost(width - 2 * (MIN_PILL.roundToPx() + gap))
        .coerceAtLeast(min(MIN_DRAWING.roundToPx(), width))
    val pillWidth = ((width - drawing) / 2 - gap).coerceAtLeast(0)
    val art = measurables.first().measure(Constraints.fixed(drawing, drawing))
    val pills = measurables.drop(1).map { it.measure(Constraints(maxWidth = pillWidth)) }

    // Each side's pills, top to bottom in the order of their parts, as near their part as fits.
    val tops = IntArray(pills.size)
    parts.indices.groupBy { parts[it].first.side }.values.forEach { indices ->
        var next = Int.MIN_VALUE
        indices.sortedBy { parts[it].first.y }.forEach { i ->
            tops[i] = max((parts[i].first.y * drawing).roundToInt() - pills[i].height / 2, next)
            next = tops[i] + pills[i].height + gap
        }
    }
    val shift = -min(0, tops.minOrNull() ?: 0)
    val bottom = max(drawing, pills.indices.maxOfOrNull { tops[it] + pills[it].height } ?: 0) + shift
    val artLeft = (width - drawing) / 2
    val lefts = pills.indices.map { if (parts[it].first.side == PillSide.Left) 0 else width - pills[it].width }
    links.lines = parts.mapIndexed { i, (anchor, attention) ->
        val edge = if (anchor.side == PillSide.Left) lefts[i] + pills[i].width else lefts[i]
        Link(
            from = Offset(edge.toFloat(), (tops[i] + shift + pills[i].height / 2).toFloat()),
            to = Offset(artLeft + anchor.x * drawing, shift + anchor.y * drawing),
            attention = attention,
        )
    }
    return layout(width, bottom) {
        art.place(artLeft, shift)
        pills.forEachIndexed { i, pill -> pill.place(lefts[i], tops[i] + shift) }
    }
}

/** The pills' lines, worked out when they are placed and drawn right after. */
private class HeroLinks {
    var lines: List<Link> = emptyList()
}

private data class Link(val from: Offset, val to: Offset, val attention: Boolean)

/**
 * One part and what it reports, tappable (docs/DESIGN.md, HotspotPill). A part that needs
 * attention is drawn on the warning colours AND says so to TalkBack, never by colour alone: its
 * value is a word or a figure ("Full", "2 days left").
 */
@Composable
internal fun HotspotPill(
    label: String,
    value: String,
    attention: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    val attentionText = stringResource(R.string.page_attention)
    val description = if (attention) "$label, $value, $attentionText" else "$label, $value"
    Surface(
        onClick = onClick,
        shape = PkTheme.shapes.tile,
        color = if (attention) colors.warningContainer else colors.surface,
        contentColor = if (attention) colors.onWarningContainer else colors.onSurface,
        shadowElevation = 1.dp,
        modifier = modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
    ) {
        // The pill's colours and its words carry its state; the dot is on the drawing.
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                label,
                style = PkTheme.typography.labelSmall.copy(hyphens = Hyphens.Auto),
                color = if (attention) colors.onWarningContainer else colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // A long word in a narrow pill (French at a large font) breaks with a hyphen.
            Text(
                value,
                style = PkTheme.typography.labelLarge.copy(hyphens = Hyphens.Auto),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val DRAWING_SHARE = 0.36f
private val MAX_DRAWING = 200.dp
private val MIN_DRAWING = 64.dp

/** In sp: the pills need room for their words, which grow with the font scale. */
private val MIN_PILL = 94.sp

private val GAP = 8.dp
private val DOT_RADIUS = 4.dp
private const val LINE_ALPHA = 0.6f
