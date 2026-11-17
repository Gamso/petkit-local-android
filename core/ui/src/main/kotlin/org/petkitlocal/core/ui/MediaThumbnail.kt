package org.petkitlocal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.MediaFile

/**
 * A photo's or a clip's still, fetched with the bearer header by the app's image loader; a clip
 * carries a play badge. Square of [size], or [width] wide at the picture's own [aspectRatio].
 * Decorative where its card says what it is; [onClick] makes it a button.
 */
@Composable
fun MediaThumbnail(
    file: MediaFile,
    size: Dp,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    aspectRatio: Float = 1f,
) {
    val colors = PkTheme.colors
    val clickable = if (onClick != null) {
        Modifier.selectable(selected = false, onClick = onClick, role = Role.Button)
    } else {
        Modifier
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = size, height = size / aspectRatio)
            .clip(PkTheme.shapes.tile)
            .background(colors.surfaceMuted)
            .then(clickable)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        // Until the still arrives (or if it cannot): a photo's symbol; a clip has its play badge.
        if (!file.kind.isVideo) {
            Icon(
                PkIcons.Photo,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (file.thumbnailUrl != null) {
            AsyncImage(
                model = file.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (file.kind.isVideo) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = BADGE_ALPHA)),
            ) {
                Icon(PkIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private const val BADGE_ALPHA = 0.55f
