package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * A round icon-only button on the surface colour, the size of a touch target (48 dp,
 * docs/SPEC.md §6). [contentDescription] is what TalkBack reads, so it is required: an icon-only
 * control without one is an accessibility bug.
 */
@Composable
fun PkIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = modifier.size(TOUCH_TARGET),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(ICON_SIZE))
        }
    }
}

/** The way back from a screen: a left arrow (mirrored in right-to-left languages), read "Back". */
@Composable
fun PkBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PkIconButton(PkIcons.ArrowBack, stringResource(R.string.pk_back), onClick, modifier)
}

private val TOUCH_TARGET = 48.dp
private val ICON_SIZE = 24.dp
