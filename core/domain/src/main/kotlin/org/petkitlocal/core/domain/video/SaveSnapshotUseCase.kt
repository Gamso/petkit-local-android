package org.petkitlocal.core.domain.video

import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import org.petkitlocal.core.domain.repository.SnapshotRepository

/**
 * Saves a frame of a camera's live video among the phone's pictures, named after the device and
 * the local time it was taken, e.g. `Purobot Max Pro 2 2026-11-16 20.15.03.jpg`: sortable, and
 * free of the characters a file name may not hold.
 */
class SaveSnapshotUseCase @Inject constructor(private val snapshots: SnapshotRepository, private val clock: Clock) {
    /** True once saved; false if the phone's storage refused it. */
    suspend operator fun invoke(deviceName: String, jpeg: ByteArray): Boolean =
        snapshots.save(jpeg, fileName(deviceName, LocalDateTime.now(clock)))

    companion object {
        private val STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH.mm.ss")
        private val UNSAFE = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
        private val SPACES = Regex("""\s+""")

        fun fileName(deviceName: String, at: LocalDateTime): String {
            val name = deviceName.replace(UNSAFE, " ").replace(SPACES, " ").trim().ifEmpty { "Camera" }
            return "$name ${STAMP.format(at)}.jpg"
        }
    }
}
