package org.petkitlocal.core.domain.video

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.repository.SnapshotRepository

class SaveSnapshotUseCaseTest {
    @Test
    fun `a snapshot is named after the device and the local time, without unsafe characters`() = runTest {
        assertEquals(
            "Purobot Max Pro 2 2026-11-16 20.15.03.jpg",
            SaveSnapshotUseCase.fileName("Purobot Max Pro 2", LocalDateTime.of(2026, 11, 16, 20, 15, 3)),
        )
        val at = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
        assertEquals("T5 cam 2026-01-02 03.04.05.jpg", SaveSnapshotUseCase.fileName("T5 / cam", at))
        assertEquals("Camera 2026-01-02 03.04.05.jpg", SaveSnapshotUseCase.fileName(":/", at))

        val saved = mutableListOf<String>()
        val repository = object : SnapshotRepository {
            override suspend fun save(jpeg: ByteArray, fileName: String): Boolean {
                saved += fileName
                return true
            }
        }
        val clock = Clock.fixed(Instant.parse("2026-11-16T19:15:03Z"), ZoneId.of("Europe/Paris"))
        assertEquals(true, SaveSnapshotUseCase(repository, clock)("D4H", byteArrayOf(1)))
        assertEquals(listOf("D4H 2026-11-16 20.15.03.jpg"), saved)
    }
}
