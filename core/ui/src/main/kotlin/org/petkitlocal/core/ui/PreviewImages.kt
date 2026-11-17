package org.petkitlocal.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

/**
 * For previews and screenshot tests: [content] fetches no image, and every one it would show (a
 * pet's photo, a clip's still) is drawn as a plain placeholder picture. A Robolectric capture then
 * never waits on the network, and a golden shows where a photo goes without depending on one.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun PreviewImages(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(PLACEHOLDER) },
        content = content,
    )
}

/** A muted warm grey: reads as a photo next to the initials' tinted circles, in light and dark. */
private const val PLACEHOLDER = 0xFF8C7B6B.toInt()
