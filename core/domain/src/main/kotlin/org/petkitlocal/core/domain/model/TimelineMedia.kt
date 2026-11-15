package org.petkitlocal.core.domain.model

/**
 * An episode's photos and videos (`Media`), as absolute URLs on the user's server. They are
 * fetched with the bearer header like every other request, never with a token in the URL.
 */
data class TimelineMedia(
    /** Photos first, then clips, in the order a viewer pages through them. */
    val files: List<MediaFile>,
    /** A recording is expected but still being assembled. */
    val videoPending: Boolean = false,
) {
    val photos: List<MediaFile> get() = files.filter { !it.kind.isVideo }

    val clips: List<MediaFile> get() = files.filter { it.kind.isVideo }
}

/** One photo or video, and the still that stands for it in a list. */
data class MediaFile(val kind: MediaKind, val url: String, val thumbnailUrl: String?)

/** The slots of `Media`, in the order they are shown. */
enum class MediaKind(val isVideo: Boolean) {
    /** The event's poster. */
    Snapshot(isVideo = false),

    /** A "check waste" photo. */
    Waste(isVideo = false),

    /** A stool analysis photo. */
    Health(isVideo = false),

    /** The short event clip. */
    Highlight(isVideo = true),

    /** The whole recording, once assembled. */
    Playback(isVideo = true),

    /** The recording sped up. */
    Timelapse(isVideo = true),
}
