package org.petkitlocal.feature.live

import androidx.media3.common.C
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LiveDelayTest {
    @Test
    fun `HLS with wall-clock times reports its live offset`() {
        assertEquals(
            2_400.milliseconds,
            liveDelay(2_400, live = true, durationMs = 12_000, positionMs = 5_000, bufferedMs = 6_000),
        )
    }

    @Test
    fun `other HLS is as far behind as the newest segment's end`() {
        assertEquals(
            3_500.milliseconds,
            liveDelay(C.TIME_UNSET, live = true, durationMs = 12_000, positionMs = 8_500, bufferedMs = 9_000),
        )
    }

    @Test
    fun `the WebSocket is as far behind as what it received and has not shown yet`() {
        assertEquals(
            450.milliseconds,
            liveDelay(C.TIME_UNSET, live = false, durationMs = C.TIME_UNSET, positionMs = 10_050, bufferedMs = 10_500),
        )
        assertNull(liveDelay(C.TIME_UNSET, live = false, durationMs = C.TIME_UNSET, positionMs = 900, bufferedMs = 0))
    }

    @Test
    fun `a delay is never negative`() {
        assertEquals(
            0.milliseconds,
            liveDelay(C.TIME_UNSET, live = true, durationMs = 4_000, positionMs = 4_200, bufferedMs = 4_200),
        )
    }
}
