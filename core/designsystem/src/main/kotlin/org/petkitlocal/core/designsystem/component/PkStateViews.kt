package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/*
 * The four states every screen shows besides its content (docs/DESIGN.md, StateViews): loading,
 * empty, error with retry, and the offline banner over the last known data.
 */

/** Skeleton cards standing in for content that is loading. Static, so screenshots are stable. */
@Composable
fun PkLoadingView(modifier: Modifier = Modifier, cards: Int = 3) {
    val label = stringResource(R.string.pk_loading)
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        repeat(cards) {
            Column(
                verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PkTheme.shapes.card)
                    .background(PkTheme.colors.surface)
                    .padding(PkTheme.spacing.m),
            ) {
                SkeletonLine(Modifier.fillMaxWidth(SKELETON_TITLE_WIDTH))
                SkeletonLine(Modifier.fillMaxWidth(SKELETON_BODY_WIDTH))
            }
        }
    }
}

private const val SKELETON_TITLE_WIDTH = 0.45f
private const val SKELETON_BODY_WIDTH = 0.8f

@Composable
private fun SkeletonLine(modifier: Modifier) {
    Box(
        modifier
            .height(14.dp)
            .clip(PkTheme.shapes.chip)
            .background(PkTheme.colors.surfaceMuted),
    )
}

/** Nothing to show yet: one plain line, and an icon for the eye. */
@Composable
fun PkEmptyView(message: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
        modifier = modifier.fillMaxWidth().padding(PkTheme.spacing.xl),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(PkTheme.shapes.tile)
                .background(PkTheme.colors.accentContainer),
        ) {
            Icon(PkIcons.History, contentDescription = null, tint = PkTheme.colors.onAccentContainer)
        }
        Text(
            message,
            style = PkTheme.typography.bodyLarge,
            color = PkTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Something failed: what, in one line, and a way to try again. */
@Composable
fun PkErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .padding(PkTheme.spacing.xl)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(PkIcons.Warning, contentDescription = null, tint = PkTheme.colors.warning, modifier = Modifier.size(32.dp))
        Text(
            message,
            style = PkTheme.typography.bodyLarge,
            color = PkTheme.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        PkButton(stringResource(R.string.pk_retry), onClick = onRetry, icon = PkIcons.Refresh)
    }
}

/** The server is unreachable; what is on screen is the last known data. */
@Composable
fun PkOfflineBanner(modifier: Modifier = Modifier, message: String = stringResource(R.string.pk_offline_banner)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .background(PkTheme.colors.warningContainer)
            .padding(horizontal = PkTheme.spacing.m, vertical = PkTheme.spacing.s)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            PkIcons.Offline,
            contentDescription = null,
            tint = PkTheme.colors.onWarningContainer,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.size(PkTheme.spacing.s))
        Text(message, style = PkTheme.typography.labelLarge, color = PkTheme.colors.onWarningContainer)
    }
}
