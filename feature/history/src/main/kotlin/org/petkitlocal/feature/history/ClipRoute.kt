package org.petkitlocal.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind

/** The clip a [ClipDestination] names, and the factory of its player. */
@HiltViewModel
class ClipViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    /** Builds the clip player; the screen creates and releases it. */
    val clipPlayerFactory: ClipPlayerFactory,
) : ViewModel() {
    val clip: MediaFile = MediaFile(
        // A kind this app no longer knows only changes the title: still a clip.
        kind = MediaKind.entries.firstOrNull { it.isVideo && it.name == savedStateHandle[CLIP_KIND_KEY] }
            ?: MediaKind.Highlight,
        url = checkNotNull(savedStateHandle[CLIP_URL_KEY]) { "no $CLIP_URL_KEY argument" },
        thumbnailUrl = null,
    )
}

/**
 * A clip opened from outside History, in the same player as History's (SPEC.md §5.4): played
 * in-app, seekable, full screen. Closing it, or going back, returns to where it was opened. The
 * screen it was opened from is stopped meanwhile, so a live video under it stops too.
 */
@Composable
internal fun ClipRoute(onBack: () -> Unit, viewModel: ClipViewModel = hiltViewModel()) {
    ClipScreen(viewModel.clip, viewModel.clipPlayerFactory, onClose = onBack)
}

@Composable
internal fun ClipScreen(clip: MediaFile, playerFactory: ClipPlayerFactory?, onClose: () -> Unit) {
    // Behind the player's full-screen window, while it opens and closes: black, like the player.
    Box(Modifier.fillMaxSize().background(Color.Black))
    ClipPlayer(clip, playerFactory, onClose = onClose)
}
