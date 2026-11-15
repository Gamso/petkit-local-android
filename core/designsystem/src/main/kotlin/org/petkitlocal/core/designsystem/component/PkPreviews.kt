package org.petkitlocal.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * The preview matrix of docs/DESIGN.md: light, dark, French, and 130 % font scale. The
 * Roborazzi tests render the same samples in all eight combinations.
 */
@Preview(name = "Light", widthDp = PREVIEW_WIDTH)
@Preview(name = "Dark", widthDp = PREVIEW_WIDTH, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "French", widthDp = PREVIEW_WIDTH, locale = "fr")
@Preview(name = "Font 130 %", widthDp = PREVIEW_WIDTH, fontScale = 1.3f)
annotation class PkPreviews

const val PREVIEW_WIDTH = 360

/** A themed screen background with a gutter, for previews and screenshot tests. */
@Composable
fun PkPreviewSurface(content: @Composable () -> Unit) {
    PkTheme {
        Box(Modifier.fillMaxWidth().background(PkTheme.colors.background).padding(16.dp)) {
            content()
        }
    }
}
