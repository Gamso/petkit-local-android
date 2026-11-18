package org.petkitlocal.core.designsystem.photo

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * A model's product photo ([DevicePhoto]), fitted and centred in its bounds: the photo padded to a
 * square, as the pills' positions are given. Decorative: the page or card names the model.
 *
 * The photo's white background is not cut out (its pixels are never edited, which would also eat
 * the shading of a white product): it is drawn with a multiply blend, so white takes the colour of
 * what is under it and the product keeps its own shades, as in PetKit's app. Under it is
 * [backdrop] when given, drawn here in [shape]; otherwise whatever its parent drew in the same
 * layer (the page's tinted gradient), so no layer may come between them. In dark, the same blend
 * would sink a white product into the page: the photo then lies on the palette's light
 * `photoPlate` instead.
 *
 * [decodeSize] is about the largest side it is shown at: the image is decoded no larger than
 * needed for it, which keeps the thumbnails of a whole household light.
 */
@Composable
fun PkDevicePhoto(
    photo: DevicePhoto,
    decodeSize: Dp,
    modifier: Modifier = Modifier,
    backdrop: Color = Color.Unspecified,
    shape: Shape = PkTheme.shapes.tile,
) {
    val resources = LocalResources.current
    val target = with(LocalDensity.current) { decodeSize.roundToPx() }
    val image = remember(photo, target) { decodePhoto(resources, photo.drawable, target) }
    val plate = PkTheme.colors.photoPlate
    val under = if (plate.isSpecified) plate else backdrop
    Canvas(modifier) {
        if (under.isSpecified) drawOutline(shape.createOutline(size, layoutDirection, this), under)
        val scale = min(size.width / image.width, size.height / image.height)
        val width = (image.width * scale).roundToInt()
        val height = (image.height * scale).roundToInt()
        drawImage(
            image,
            dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height),
            blendMode = BlendMode.Multiply,
            filterQuality = FilterQuality.High,
        )
    }
}

/** Decodes [id] subsampled by a power of two, never below [target] pixels on its longer side. */
private fun decodePhoto(resources: Resources, id: Int, target: Int): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, id, bounds)
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (target > 0 && longest / (sample * 2) >= target) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeResource(resources, id, options).asImageBitmap()
}
