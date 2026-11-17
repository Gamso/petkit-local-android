package io.github.gamso.petkitlocal.mock.media

import java.nio.ByteBuffer

/**
 * The live-video test pattern: a fragmented MP4 bundled as `media/live.mp4` (6 s of ffmpeg's
 * `testsrc2`, 320x240, 16 fps, H.264 Constrained Baseline; `tools/make_live_pattern.sh` made it),
 * split into its init segment (`ftyp`+`moov`) and its media fragments (`moof`+`mdat`, one
 * keyframe each), and replayed forever.
 *
 * Fragment [n] of the endless stream is pattern fragment `n % size` with its decode time moved to
 * `n` fragment durations and its sequence number to `n + 1`, so a player sees one continuous
 * stream however many times the pattern looped. Both fields are rewritten in place: no size
 * changes, and `default-base-is-moof` keeps every data offset valid.
 */
class LivePattern(
    mp4: ByteArray,
) {
    /** `ftyp` + `moov`: what a viewer gets first. */
    val init: ByteArray

    private val fragments: List<Fragment>

    /** The media timescale (`mdhd`), in ticks per second. */
    val timescale: Long

    /** Every fragment's duration, in [timescale] ticks: the pattern's are all the same. */
    val fragmentTicks: Long

    /** The H.264 codec string from `avcC`, e.g. `avc1.42C01E`. */
    val codec: String

    val size: Int get() = fragments.size

    val fragmentMillis: Long get() = fragmentTicks * MILLIS / timescale

    /** The MIME type the live socket answers the `mse` request with. */
    val mime: String get() = "video/mp4; codecs=\"$codec\""

    init {
        val top = boxes(mp4, 0, mp4.size)
        val moov = top.single { it.type == "moov" }
        init = mp4.copyOfRange(0, moov.end)
        require(top.take(2).map { it.type } == listOf("ftyp", "moov")) { "live.mp4 must start with ftyp, moov" }
        val mdhd = find(mp4, moov, "trak", "mdia", "mdhd")
        require(mp4[mdhd.body] == 0.toByte()) { "mdhd version 1 is not handled" }
        timescale = u32(mp4, mdhd.body + MDHD_V0_TIMESCALE)
        codec = avcCodec(mp4, moov)
        fragments =
            top
                .filter { it.type == "moof" }
                .map { moof ->
                    val mdat = top.first { it.start == moof.end }
                    require(mdat.type == "mdat") { "every moof must be followed by its mdat" }
                    val mfhd = find(mp4, moof, "mfhd")
                    val tfdt = find(mp4, moof, "traf", "tfdt")
                    require(mp4[tfdt.body] == 1.toByte()) { "tfdt must be version 1 (64-bit)" }
                    Fragment(
                        bytes = mp4.copyOfRange(moof.start, mdat.end),
                        sequenceAt = mfhd.body + FULL_BOX_HEADER - moof.start,
                        decodeTimeAt = tfdt.body + FULL_BOX_HEADER - moof.start,
                        decodeTime = ByteBuffer.wrap(mp4, tfdt.body + FULL_BOX_HEADER, Long.SIZE_BYTES).long,
                    )
                }
        require(fragments.size >= 2) { "live.mp4 needs at least two fragments" }
        fragmentTicks = fragments[1].decodeTime - fragments[0].decodeTime
        fragments.forEachIndexed { i, f ->
            require(f.decodeTime == fragments[0].decodeTime + i * fragmentTicks) { "fragment $i does not last $fragmentTicks ticks" }
        }
    }

    /** Fragment [n] (0-based) of the endless stream, its decode time and sequence number moved to fit. */
    fun fragment(n: Long): ByteArray {
        require(n >= 0) { "no fragment $n" }
        val source = fragments[(n % size).toInt()]
        val out = source.bytes.copyOf()
        ByteBuffer.wrap(out).putLong(source.decodeTimeAt, n * fragmentTicks)
        ByteBuffer.wrap(out).putInt(source.sequenceAt, (n + 1).toInt())
        return out
    }

    private class Fragment(
        val bytes: ByteArray,
        val sequenceAt: Int,
        val decodeTimeAt: Int,
        val decodeTime: Long,
    )

    private data class Box(
        val type: String,
        val start: Int,
        val end: Int,
        val body: Int,
    )

    private fun boxes(
        data: ByteArray,
        from: Int,
        to: Int,
    ): List<Box> {
        val out = mutableListOf<Box>()
        var at = from
        while (at < to) {
            val size = u32(data, at)
            val type = String(data, at + Int.SIZE_BYTES, Int.SIZE_BYTES, Charsets.US_ASCII)
            require(size >= BOX_HEADER && at + size <= to) { "bad $type box at $at" }
            out += Box(type, at, (at + size).toInt(), at + BOX_HEADER)
            at += size.toInt()
        }
        return out
    }

    /** The box at [path] below [parent] (plain container boxes only). */
    private fun find(
        data: ByteArray,
        parent: Box,
        vararg path: String,
    ): Box {
        var box = parent
        for (type in path) box = boxes(data, box.body, box.end).first { it.type == type }
        return box
    }

    private fun avcCodec(
        data: ByteArray,
        moov: Box,
    ): String {
        val stsd = find(data, moov, "trak", "mdia", "minf", "stbl", "stsd")
        // stsd: full box header and an entry count, then the sample entries.
        val avc1 = boxes(data, stsd.body + FULL_BOX_HEADER + Int.SIZE_BYTES, stsd.end).first { it.type == "avc1" }
        val avcC = boxes(data, avc1.body + VISUAL_SAMPLE_ENTRY, avc1.end).first { it.type == "avcC" }
        // configurationVersion, then AVCProfileIndication, profile_compatibility, AVCLevelIndication.
        val profile = data.copyOfRange(avcC.body + 1, avcC.body + 1 + AVC_PROFILE_BYTES)
        return "avc1." + profile.joinToString("") { "%02X".format(it) }
    }

    companion object {
        private const val BOX_HEADER = 8
        private const val FULL_BOX_HEADER = 4
        private const val MDHD_V0_TIMESCALE = 12
        private const val VISUAL_SAMPLE_ENTRY = 78
        private const val AVC_PROFILE_BYTES = 3
        private const val MILLIS = 1000L

        private fun u32(
            data: ByteArray,
            at: Int,
        ): Long = ByteBuffer.wrap(data, at, Int.SIZE_BYTES).int.toLong() and 0xFFFFFFFFL

        /** The bundled pattern. */
        val bundled: LivePattern by lazy {
            LivePattern(
                LivePattern::class.java.getResourceAsStream("/media/live.mp4")?.use { it.readBytes() }
                    ?: error("media/live.mp4 is missing from the classpath"),
            )
        }
    }
}
