package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * A card: surface colour, 22 dp corners, a hairline shadow (docs/DESIGN.md: elevation is a
 * hairline only). Clickable when [onClick] is given.
 */
@Composable
fun PkCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(PkTheme.spacing.m), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = PkTheme.shapes.card,
            color = PkTheme.colors.surface,
            contentColor = PkTheme.colors.onSurface,
            shadowElevation = HAIRLINE,
            modifier = modifier,
            content = inner,
        )
    } else {
        Surface(
            shape = PkTheme.shapes.card,
            color = PkTheme.colors.surface,
            contentColor = PkTheme.colors.onSurface,
            shadowElevation = HAIRLINE,
            modifier = modifier,
            content = inner,
        )
    }
}

private val HAIRLINE = 1.dp
