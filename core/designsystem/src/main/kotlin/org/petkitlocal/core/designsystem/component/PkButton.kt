package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

enum class PkButtonStyle {
    /** The one main action of a screen or sheet: filled with the accent. */
    Primary,

    /** Any other action: a muted tile. */
    Secondary,

    /** Confirms something that cannot be undone (sign out, a destructive device action). */
    Danger,

    /** A low-emphasis action next to another button: cancel, dismiss. */
    Text,
}

private const val DISABLED_ALPHA = 0.38f

/** The app's button: at least 48 dp tall, label in sentence case, optional leading icon. */
@Composable
fun PkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PkButtonStyle = PkButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = PkTheme.colors
    val (container, content) = when (style) {
        PkButtonStyle.Primary -> colors.accent to colors.onAccent
        PkButtonStyle.Secondary -> colors.surfaceMuted to colors.onSurface
        PkButtonStyle.Danger -> colors.danger to colors.onDanger
        PkButtonStyle.Text -> Color.Transparent to colors.accent
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PkTheme.shapes.button,
        color = container,
        contentColor = content,
        modifier = modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { role = Role.Button },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = PkTheme.spacing.l, vertical = PkTheme.spacing.s),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(PkTheme.spacing.xs))
            }
            Text(text, style = PkTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
