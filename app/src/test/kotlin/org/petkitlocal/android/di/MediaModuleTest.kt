package org.petkitlocal.android.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaModuleTest {
    @Test
    fun `the emulator's host decoders go last, every other one keeps its place`() {
        val codecs = listOf("c2.goldfish.h264.decoder", "c2.qti.avc.decoder", "c2.android.avc.decoder")

        assertEquals(
            listOf("c2.qti.avc.decoder", "c2.android.avc.decoder", "c2.goldfish.h264.decoder"),
            emulatorCodecsLast(codecs) { it },
        )
        assertEquals(
            listOf("c2.exynos.h264.decoder", "c2.android.avc.decoder"),
            emulatorCodecsLast(listOf("c2.exynos.h264.decoder", "c2.android.avc.decoder")) {
                it
            },
        )
    }
}
