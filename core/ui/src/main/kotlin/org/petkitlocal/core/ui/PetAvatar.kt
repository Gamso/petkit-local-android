package org.petkitlocal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * A pet's photo over its initial on its colour, which is what shows until (or unless) the photo
 * loads (docs/DESIGN.md, PetChip). The photo is fetched by the app's image loader, which carries
 * the bearer header and caches it. With no [colorIndex], a pet the device could not tell or one
 * no longer in the household, it is a paw. Decorative: the name is always written next to it.
 */
@Composable
fun PetAvatar(
    name: String,
    photoUrl: String?,
    colorIndex: Int?,
    size: Dp,
    modifier: Modifier = Modifier,
    initialStyle: TextStyle = if (size >= LARGE_AVATAR) {
        PkTheme.typography.headlineLarge
    } else {
        PkTheme.typography.titleMedium
    },
) {
    if (colorIndex == null) {
        UnknownPetAvatar(size, modifier)
        return
    }
    val color = PkTheme.colors.pet(colorIndex)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = AVATAR_TINT)),
    ) {
        Text(name.take(1).uppercase(), style = initialStyle, color = color)
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** A pet nobody could name: a paw on the muted surface. */
@Composable
fun UnknownPetAvatar(size: Dp, modifier: Modifier = Modifier) {
    SymbolAvatar(PkIcons.Pets, PkTheme.colors.onSurfaceMuted, size, modifier)
}

/** A symbol in a circle the size of an avatar, so a list mixes them in one column. */
@Composable
fun SymbolAvatar(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceMuted),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * SYMBOL_SHARE))
    }
}

private const val AVATAR_TINT = 0.16f
private const val SYMBOL_SHARE = 0.55f
private val LARGE_AVATAR = 64.dp
