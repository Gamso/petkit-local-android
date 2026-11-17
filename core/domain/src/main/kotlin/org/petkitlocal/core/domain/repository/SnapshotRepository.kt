package org.petkitlocal.core.domain.repository

/** Where the live screen's snapshots go: the phone's own pictures, not the server. */
interface SnapshotRepository {
    /** Saves [jpeg] as [fileName] among the phone's pictures, where the gallery shows it; false if it could not. */
    suspend fun save(jpeg: ByteArray, fileName: String): Boolean
}
