package org.petkitlocal.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.SessionEndReason

/* The messages of the sign-in screen that belong to no single field. */

@Composable
internal fun SessionEndedBanner(reason: SessionEndReason) {
    Notice(
        text = stringResource(
            when (reason) {
                SessionEndReason.PasswordRemoved -> R.string.auth_session_ended_password_removed
                SessionEndReason.InvalidToken, SessionEndReason.ClosedByServer -> R.string.auth_session_ended
            },
        ),
        modifier = Modifier.testTag(SignInTestTags.SESSION_ENDED),
    )
}

@Composable
internal fun ErrorCard(text: String) = Notice(text, Modifier.testTag(SignInTestTags.ERROR))

/**
 * A warning-coloured message with an icon (the colour is never the only signal), and the button
 * that resolves it when there is one.
 */
@Composable
internal fun Notice(text: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .background(PkTheme.colors.warningContainer)
            .padding(PkTheme.spacing.m)
            // Read as one announcement, and again whenever it changes.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                PkIcons.Warning,
                contentDescription = null,
                tint = PkTheme.colors.onWarningContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(text, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onWarningContainer)
        }
        action?.let { (label, onClick) ->
            PkButton(label, onClick = onClick, style = PkButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}
