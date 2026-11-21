package org.petkitlocal.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.ui.live.LIVE_SCRIM_ALPHA
import org.petkitlocal.core.ui.live.LivePicture
import org.petkitlocal.core.ui.live.LiveStatus
import org.petkitlocal.core.ui.live.LiveStatusContent
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.petkitlocal.core.ui.live.liveStatusOf

/**
 * Over the picture: what the session is doing when it is not simply playing (core:ui's
 * [liveStatusOf], as on a camera page's live card), or that there is no camera to show.
 */
@Composable
internal fun SessionOverlay(uiState: LiveUiState, onRetry: () -> Unit) {
    val status = when (uiState.availability) {
        LiveAvailability.Loading -> LiveStatus(busy = true, message = null)
        LiveAvailability.NotACamera -> LiveStatus(busy = false, message = stringResource(R.string.live_not_a_camera))
        LiveAvailability.Gone -> LiveStatus(busy = false, message = stringResource(R.string.live_gone))
        LiveAvailability.Camera -> liveStatusOf(uiState.session)
    } ?: return
    LiveStatusContent(
        status = status,
        onRetry = onRetry,
        modifier = Modifier
            .fillMaxSize()
            .background(LivePicture.copy(alpha = LIVE_SCRIM_ALPHA))
            .padding(PkTheme.spacing.m)
            .testTag(LiveVideoTestTags.STATUS),
    )
}
