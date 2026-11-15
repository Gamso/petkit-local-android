package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.designsystem.component.PkModeChip
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.theme.PkSpacing
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.litter.DeviceMode
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R

/**
 * The header's mode chips, one per switch (auto clean, auto deodorize). A tap writes the switch;
 * the chip follows the device's next report, never the tap (ADR 0002). While a chip's write is in
 * flight ([sending] holds its key), its taps are ignored.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
internal fun ModeChips(
    modes: List<DeviceMode>,
    sending: Set<String>,
    onToggle: (key: String, on: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (modes.isEmpty()) return
    val labels = rememberServerLabels()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        modes.forEach { mode ->
            PkModeChip(
                label = labels.entity(mode.key, mode.name),
                on = mode.on,
                onToggle = { if (mode.key !in sending) onToggle(mode.key, it) },
                modifier = Modifier.testTag(PageTestTags.mode(mode.key)),
            )
        }
    }
}

/** Why what is shown may be stale: the server is out of reach, or the last refresh failed. */
@Composable
internal fun RefreshBanner(offline: Boolean, error: DomainError?, modifier: Modifier = Modifier) {
    when {
        offline -> PkOfflineBanner(modifier)
        error is DomainError.SessionEnded -> PkOfflineBanner(modifier, stringResource(R.string.page_session_ended))
        error != null -> PkOfflineBanner(modifier, stringResource(R.string.page_refresh_failed))
    }
}

/** The page's top: the family's tint fading into the background, under the status bar. */
@Composable
internal fun PageTop(tint: Color, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(tint, PkTheme.colors.background)))
            .padding(top = insets.calculateTopPadding() + PkTheme.spacing.s)
            .gutter(),
    ) { content() }
}

/** A screen's left and right margin (PkSpacing.gutter). */
internal fun Modifier.gutter(): Modifier = padding(horizontal = PkSpacing().gutter)

/** Test tags of the litter box and feeder pages' own nodes, for the UI and end-to-end tests. */
object PageTestTags {
    const val LIST = "page:list"

    fun mode(key: String): String = "page:mode:$key"

    fun hotspot(key: String): String = "page:hotspot:$key"
}
