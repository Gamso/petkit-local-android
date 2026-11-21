package org.petkitlocal.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.ui.live.LIVE_PILL_ALPHA
import org.petkitlocal.core.ui.live.LiveFullscreenButton
import org.petkitlocal.core.ui.live.LiveMuteButton
import org.petkitlocal.core.ui.live.LivePicture
import org.petkitlocal.core.ui.live.OnLivePicture
/** Under the picture: sound, snapshot, full screen; what the snapshot came to; then Talk. */
@Composable
internal fun LiveControls(uiState: LiveUiState, callbacks: LiveCallbacks) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .padding(PkTheme.spacing.gutter),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.m)) {
            LiveMuteButton(uiState.soundMuted, callbacks.onToggleMute)
            SnapshotButton(uiState.savingSnapshot, callbacks.onSnapshot)
            LiveFullscreenButton({ callbacks.onFullscreen(true) })
        }
        SnapshotNotice(uiState.snapshot)
        TalkControls(uiState.talk, callbacks)
    }
}

/** Full screen: the same controls over the picture's bottom corner, and the way out of it. */
@Composable
internal fun BoxScope.FullscreenControls(uiState: LiveUiState, callbacks: LiveCallbacks) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .safeDrawingPadding()
            .padding(PkTheme.spacing.m),
    ) {
        SnapshotNotice(
            uiState.snapshot,
            Modifier.clip(PkTheme.shapes.chip).background(LivePicture.copy(alpha = LIVE_PILL_ALPHA)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
            LiveMuteButton(uiState.soundMuted, callbacks.onToggleMute)
            SnapshotButton(uiState.savingSnapshot, callbacks.onSnapshot)
            PkIconButton(PkIcons.FullscreenExit, stringResource(R.string.live_exit_fullscreen), {
                callbacks.onFullscreen(false)
            })
        }
    }
}

/** Takes a snapshot; one at a time, while the last one is still being saved. */
@Composable
private fun SnapshotButton(saving: Boolean, onSnapshot: () -> Unit) {
    PkIconButton(PkIcons.PhotoCamera, stringResource(R.string.live_snapshot), { if (!saving) onSnapshot() })
}

@Composable
internal fun SnapshotNotice(result: SnapshotResult?, modifier: Modifier = Modifier) {
    if (result == null) return
    val text = when (result) {
        SnapshotResult.Saved -> R.string.live_snapshot_saved
        SnapshotResult.Failed -> R.string.live_snapshot_failed
        SnapshotResult.NoPicture -> R.string.live_snapshot_no_picture
    }
    Text(
        stringResource(text),
        style = PkTheme.typography.bodyMedium,
        color = OnLivePicture,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = PkTheme.spacing.s, vertical = PkTheme.spacing.xxs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
