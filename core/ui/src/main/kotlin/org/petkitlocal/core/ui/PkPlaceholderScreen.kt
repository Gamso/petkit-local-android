package org.petkitlocal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * The frame of a screen that is not built yet: its title, which milestone brings it
 * (docs/ROADMAP.md), and a way back. [content] adds anything the placeholder wants to prove.
 */
@Composable
fun PkPlaceholderScreen(
    title: String,
    milestone: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(PkTheme.spacing.gutter),
    ) {
        if (onBack != null) {
            PkButton(stringResource(R.string.core_ui_back), onClick = onBack, style = PkButtonStyle.Text)
        }
        Text(
            title,
            style = PkTheme.typography.display,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (milestone == null) {
            PkLoadingView(cards = 1)
        } else {
            PkCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.core_ui_placeholder, milestone),
                    style = PkTheme.typography.bodyLarge,
                    color = PkTheme.colors.onSurfaceMuted,
                )
            }
        }
        content()
    }
}
