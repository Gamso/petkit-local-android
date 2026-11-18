package org.petkitlocal.feature.device.litter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.designsystem.photo.PkDevicePhoto
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.ui.rememberServerLabels

/** The product photo [device]'s page shows as its hero, or null to keep our drawing. */
internal fun heroPhoto(device: Device): DevicePhoto? =
    DevicePhoto.of(device.type, device.model)?.takeIf(PhotoSpots::placesParts)

/**
 * A device page's hero with the model's photo (issue #69, layout A): the photo fills the hero,
 * and each [HotspotPill] sits right beside a dot on its part, as [PhotoSpots] places it for that
 * photo ([PhotoPillLayout] keeps it inside and away from the others). The pills are the drawing's
 * hero's: same words, colours, states and taps. A part the photo does not place gets its pill
 * under the photo, without a dot. The hero is a picture, and is not mirrored.
 */
@Composable
internal fun PhotoHero(
    hotspots: List<Hotspot>,
    photo: DevicePhoto,
    onHotspot: (DevicePart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    val labels = rememberServerLabels()
    val spots = hotspots.map { PhotoSpots.of(photo, it.part) }
    Layout(
        content = {
            PkDevicePhoto(
                photo,
                decodeSize = MAX_FRAME,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    hotspots.forEachIndexed { i, hotspot ->
                        val spot = spots[i] ?: return@forEachIndexed
                        val (x, y) = PhotoPillLayout.onPhoto(spot)
                        val center = Offset(x * size.width, y * size.height)
                        drawCircle(colors.surface, radius = (DOT_RADIUS + DOT_RING).toPx(), center = center)
                        drawCircle(if (hotspot.attention) colors.warning else colors.accent, DOT_RADIUS.toPx(), center)
                    }
                },
            )
            hotspots.forEach { hotspot ->
                HotspotPill(
                    label = hotspotLabel(hotspot, labels),
                    value = readingText(hotspot.reading, labels, short = true),
                    attention = hotspot.attention,
                    onClick = { onHotspot(hotspot.part) },
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val frame = min(width, MAX_FRAME.roundToPx())
        val start = (width - frame) / 2
        val geometry = PhotoPillLayout(frame, GAP.roundToPx(), MARGIN.roundToPx(), SPACING.roundToPx())
        val art = measurables.first().measure(Constraints.fixed(geometry.photoSize, geometry.photoSize))
        val pillConstraints = Constraints(maxWidth = (frame * PILL_SHARE).roundToInt())
        val pills = measurables.drop(1).map { it.measure(pillConstraints) }
        val placement = geometry.place(
            pills.mapIndexed { i, pill ->
                PillRequest(pill.width, pill.height, spots[i]?.let(geometry::pointOf), spots[i]?.side ?: PillSide.Right)
            },
        )
        layout(width, placement.height) {
            art.place(start + geometry.photoOffset, geometry.photoOffset)
            pills.forEachIndexed { i, pill -> pill.place(start + placement.positions[i].x, placement.positions[i].y) }
        }
    }
}

/** Beyond a phone's width the photo stops growing, and stays centred. */
private val MAX_FRAME = 400.dp

/** A pill's widest, as a share of the frame: two side by side still leave the product seen. */
private const val PILL_SHARE = 0.46f
private val GAP = 8.dp
private val MARGIN = 4.dp
private val SPACING = 4.dp
private val DOT_RADIUS = 4.dp
private val DOT_RING = 2.dp
