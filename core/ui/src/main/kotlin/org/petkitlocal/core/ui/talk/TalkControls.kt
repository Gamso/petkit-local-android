package org.petkitlocal.core.ui.talk

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.ui.R

/**
 * Talk, under a live picture (the live screen's controls, a camera page's live card, its full
 * screen): held to speak through the camera's speaker, walkie-talkie style. Under it, while
 * talking, what is happening; otherwise what was said last (why it cannot talk, the microphone, an
 * error), announced by TalkBack as it appears; and, when the server says the camera cannot talk
 * now, the short reason. Talk stays pressable then, and pressing it says what is missing and how
 * to fix it.
 */
@Composable
fun TalkControls(talk: TalkUiState, callbacks: TalkCallbacks, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = modifier,
    ) {
        TalkButton(talk, callbacks)
        when {
            talk.phase == TalkPhase.Talking -> TalkingIndicator()

            talk.notice != null -> TalkNoticeLine(talk.notice, callbacks.onOpenAppSettings)

            talk.phase == TalkPhase.Idle && talk.availability != TalkAvailability.Available -> Text(
                reasonText(talk.availability),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(TalkTestTags.REASON),
            )
        }
    }
}

/**
 * The button. A finger down presses it and a finger up (or sliding off) lets it go. TalkBack has
 * no practical hold, so its double tap is a toggle instead ([TalkCallbacks.onTalkToggled]),
 * named by the action TalkBack reads ("double tap to start talking").
 */
@Composable
private fun TalkButton(talk: TalkUiState, callbacks: TalkCallbacks) {
    val colors = PkTheme.colors
    val available = talk.availability == TalkAvailability.Available
    val enabled = talk.phase != TalkPhase.Stopping
    val (container, content) = when {
        talk.phase == TalkPhase.Talking -> colors.danger to colors.onDanger
        available -> colors.accent to colors.onAccent
        else -> colors.surfaceMuted to colors.onSurface
    }
    val words = talkButtonWords(talk.phase, available)
    val press by rememberUpdatedState(callbacks.onTalkPressed)
    val release by rememberUpdatedState(callbacks.onTalkReleased)
    val toggle by rememberUpdatedState(callbacks.onTalkToggled)
    Surface(
        shape = PkTheme.shapes.button,
        color = container,
        contentColor = content,
        modifier = Modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .testTag(TalkTestTags.TALK)
            .holdToTalk(enabled, words, onPress = { press() }, onRelease = { release() }, onToggle = { toggle() }),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = PkTheme.spacing.l, vertical = PkTheme.spacing.s),
        ) {
            Icon(
                if (available) PkIcons.Mic else PkIcons.MicOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(PkTheme.spacing.xs))
            Text(words.label, style = PkTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

/** What Talk says: on the button, and to TalkBack (its name, its state, what a double tap does). */
private class TalkButtonWords(val label: String, val name: String, val state: String?, val action: String)

@Composable
private fun talkButtonWords(phase: TalkPhase, available: Boolean) = TalkButtonWords(
    label = stringResource(
        when (phase) {
            TalkPhase.Idle -> if (available) R.string.live_talk_hold else R.string.live_talk
            TalkPhase.Connecting -> R.string.live_talk_connecting
            TalkPhase.Talking -> R.string.live_talk_talking
            TalkPhase.Stopping -> R.string.live_talk_stopping
        },
    ),
    name = stringResource(R.string.live_talk),
    state = when (phase) {
        TalkPhase.Idle -> null
        TalkPhase.Connecting -> stringResource(R.string.live_talk_state_connecting)
        TalkPhase.Talking -> stringResource(R.string.live_talk_state_talking)
        TalkPhase.Stopping -> stringResource(R.string.live_talk_state_stopping)
    },
    action = stringResource(
        when (phase) {
            TalkPhase.Connecting -> R.string.live_talk_action_cancel
            TalkPhase.Talking, TalkPhase.Stopping -> R.string.live_talk_action_stop
            TalkPhase.Idle -> R.string.live_talk_action_start
        },
    ),
)

/**
 * The press and the release from the finger, the toggle from TalkBack's double tap. The release
 * comes however the press ends, the finger lifted, slid off or the button gone.
 */
private fun Modifier.holdToTalk(
    enabled: Boolean,
    words: TalkButtonWords,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onToggle: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        awaitFirstDown()
        onPress()
        try {
            waitForUpOrCancellation()
        } finally {
            onRelease()
        }
    }
}.clearAndSetSemantics {
    role = Role.Button
    contentDescription = words.name
    words.state?.let { stateDescription = it }
    if (!enabled) disabled()
    onClick(label = words.action) {
        onToggle()
        true
    }
}

/** While talking: a red dot and what is happening, announced once. */
@Composable
private fun TalkingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .testTag(TalkTestTags.INDICATOR)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(PkTheme.colors.danger, CircleShape),
        )
        Spacer(Modifier.width(PkTheme.spacing.xs))
        Text(
            stringResource(R.string.live_talk_indicator),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun TalkNoticeLine(notice: TalkNotice, onOpenAppSettings: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(TalkTestTags.NOTICE),
    ) {
        Text(
            noticeText(notice),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (notice == TalkNotice.MicrophoneDenied) {
            PkButton(
                text = stringResource(R.string.live_talk_open_settings),
                onClick = onOpenAppSettings,
                style = PkButtonStyle.Text,
            )
        }
    }
}

@Composable
private fun reasonText(availability: TalkAvailability): String = stringResource(
    when (availability) {
        TalkAvailability.NeedsPatcher -> R.string.live_talk_reason_needs_patcher
        TalkAvailability.DeviceUnreachable -> R.string.live_talk_reason_unreachable
        TalkAvailability.Unsupported -> R.string.live_talk_reason_unsupported
        TalkAvailability.ServerTooOld -> R.string.live_talk_reason_server_too_old
        TalkAvailability.Unknown, TalkAvailability.Available -> R.string.live_talk_reason_unknown
    },
)

@Composable
private fun noticeText(notice: TalkNotice): String = when (notice) {
    is TalkNotice.Unavailable -> stringResource(
        when (notice.reason) {
            TalkAvailability.NeedsPatcher -> R.string.live_talk_fix_needs_patcher
            TalkAvailability.DeviceUnreachable -> R.string.live_talk_fix_unreachable
            TalkAvailability.Unsupported -> R.string.live_talk_fix_unsupported
            TalkAvailability.ServerTooOld -> R.string.live_talk_fix_server_too_old
            TalkAvailability.Unknown, TalkAvailability.Available -> R.string.live_talk_fix_unknown
        },
    )

    TalkNotice.MicrophoneReason -> stringResource(R.string.live_talk_mic_reason)

    TalkNotice.MicrophoneAllowed -> stringResource(R.string.live_talk_mic_allowed)

    TalkNotice.MicrophoneDenied -> stringResource(R.string.live_talk_mic_denied)

    TalkNotice.HoldToTalk -> stringResource(R.string.live_talk_hold_hint)

    TalkNotice.SoundOffWhileTalking -> stringResource(R.string.live_talk_sound_off)

    is TalkNotice.Failed -> failureText(notice.failure)
}

@Composable
private fun failureText(failure: TalkFailure): String = when (failure) {
    TalkFailure.NotACamera -> stringResource(R.string.live_talk_error_not_a_camera)
    TalkFailure.NeedsPatcher -> stringResource(R.string.live_talk_fix_needs_patcher)
    TalkFailure.DeviceUnreachable -> stringResource(R.string.live_talk_error_unreachable)
    TalkFailure.Busy -> stringResource(R.string.live_talk_error_busy)
    is TalkFailure.TranscoderFailed -> stringResource(R.string.live_talk_error_transcoder, failure.message)
    TalkFailure.IdleTimeout -> stringResource(R.string.live_talk_error_idle)
    is TalkFailure.Other -> stringResource(R.string.live_talk_error_other, failure.message.ifBlank { failure.code })
    TalkFailure.ServerStopping -> stringResource(R.string.live_talk_error_server_stopping)
    TalkFailure.SessionEnded -> stringResource(R.string.live_talk_error_session)
    is TalkFailure.Refused -> refusedText(failure.error)
    is TalkFailure.Network -> stringResource(R.string.live_talk_error_network)
    TalkFailure.MicrophoneUnavailable -> stringResource(R.string.live_talk_error_microphone)
}

/** A refused upgrade: a server without the talk route answers 404, which is a server too old. */
@Composable
private fun refusedText(error: DomainError): String = when (error) {
    is DomainError.NotFound -> stringResource(
        if (error.code == "device_not_found") R.string.live_gone else R.string.live_talk_fix_server_too_old,
    )

    is DomainError.Network -> stringResource(R.string.live_talk_error_network)

    else -> stringResource(R.string.live_talk_error_refused)
}

private const val DISABLED_ALPHA = 0.38f
