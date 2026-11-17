package org.petkitlocal.core.domain.video

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * One of a camera's recent clips (SPEC.md §5.7): the History card it belongs to, which says what
 * happened and when, and the clip that stands for that card.
 */
data class RecentClip(val item: TimelineItem, val clip: MediaFile)

/** How many recent clips a camera's page and its live video show. */
const val RECENT_CLIPS = 6

/**
 * The clip that stands for an episode: the short event clip when there is one, else the whole
 * recording, else the timelapse — the order of [TimelineMedia.clips]. Null when it has none yet.
 */
val TimelineMedia.featuredClip: MediaFile? get() = clips.firstOrNull()

/**
 * [deviceId]'s newest cached cards holding a clip, newest first, whatever day they are on, at
 * most [limit]. Read from the cache only (ADR 0002): it emits the last known clips at once, and
 * again when a refresh or a live update writes the cache.
 */
class ObserveRecentClipsUseCase @Inject constructor(private val timeline: TimelineRepository) {
    operator fun invoke(deviceId: Long, limit: Int = RECENT_CLIPS): Flow<List<RecentClip>> =
        timeline.observeRecentClips(deviceId, limit).map { items ->
            items.mapNotNull { item -> item.media?.featuredClip?.let { RecentClip(item, it) } }
        }
}

/** Reads [deviceId]'s newest cards from the server until [count] of them hold a clip, into the cache. */
class RefreshRecentClipsUseCase @Inject constructor(private val timeline: TimelineRepository) {
    suspend operator fun invoke(deviceId: Long, count: Int = RECENT_CLIPS): Outcome<Unit> =
        timeline.refreshRecentClips(deviceId, count)
}
