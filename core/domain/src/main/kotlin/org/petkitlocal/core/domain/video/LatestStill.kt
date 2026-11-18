package org.petkitlocal.core.domain.video

import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The latest picture a camera produced, which its page's live card shows until the live video is
 * opened: the thumbnail of its newest episode's media, and when that episode happened (null when
 * the server did not say).
 */
data class LatestStill(val thumbnailUrl: String, val at: Instant?)

/**
 * The file whose thumbnail stands for an episode: its poster when it has one, else its first photo
 * or video with a thumbnail, in the order of [TimelineMedia.files]. Null when none has one.
 */
val TimelineMedia.still: MediaFile? get() = files.firstOrNull { it.thumbnailUrl != null }

/**
 * [deviceId]'s latest still, from the cache only (ADR 0002): null while no cached card of it has a
 * picture. The refresh of its recent clips ([RefreshRecentClipsUseCase]) reads its newest cards
 * from the server, and a live update writes a new one, so the still follows both.
 */
class ObserveLatestStillUseCase @Inject constructor(private val timeline: TimelineRepository) {
    operator fun invoke(deviceId: Long): Flow<LatestStill?> =
        timeline.observeNewestWithMedia(deviceId, NEWEST_MEDIA_CARDS).map { items ->
            items.firstNotNullOfOrNull { item ->
                item.media?.still?.thumbnailUrl?.let { LatestStill(it, item.shownAt) }
            }
        }.distinctUntilChanged()

    private companion object {
        /**
         * A few cards, for a newest one whose media is only a recording still being assembled or
         * lies outside the API: the still is then the next one's.
         */
        const val NEWEST_MEDIA_CARDS = 5
    }
}
