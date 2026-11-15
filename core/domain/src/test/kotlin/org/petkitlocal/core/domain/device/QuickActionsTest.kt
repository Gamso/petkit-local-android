package org.petkitlocal.core.domain.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.repository.QuickActionsRepository

/** The user's own actions in view (issue #40): resolution against what the device lists, edits, reset. */
class QuickActionsTest {
    private fun action(key: String, destructive: Boolean = false) = DeviceAction(key, key, destructive)

    // The Pura Max's actions, in the server's order (mock catalog, t4).
    private val t4 = listOf(
        action("cleaning_start"),
        action("maintenance_stop"),
        action("deodorize"),
        action("pause"),
        action("resume"),
        action("reset"),
        action("level_litter"),
        action("finish_litter_calibration"),
        action("maintenance_start", destructive = true),
        action("dump_litter", destructive = true),
        action("reset_n50", destructive = true),
    )

    private fun layout(pinned: List<String>?, actions: List<DeviceAction> = t4): Pair<List<String>, List<String>> {
        val layout = DeviceActionOrder.layout(actions, pinned)
        return layout.main.map(DeviceAction::key) to layout.more.map(DeviceAction::key)
    }

    @Test
    fun `pinned actions are in view in the user's order, and More holds every other one in the app's order`() {
        val (main, more) = layout(listOf("dump_litter", "level_litter", "pause"))

        assertEquals(listOf("dump_litter", "level_litter", "pause"), main)
        assertEquals(
            listOf(
                "cleaning_start",
                "deodorize",
                "resume",
                "maintenance_stop",
                "reset",
                "finish_litter_calibration",
                "maintenance_start",
                "reset_n50",
            ),
            more,
        )
        // Nothing lost, nothing twice.
        assertEquals(t4.map { it.key }.sorted(), (main + more).sorted())
    }

    @Test
    fun `an action the server no longer lists gives its slot back to the app's ranking, in place`() {
        // An action pinned in the middle, then gone from the server: the best everyday action not
        // already in view takes that slot; the others keep theirs.
        val (main, more) = layout(listOf("level_litter", "vanished", "cleaning_start"))

        assertEquals(listOf("level_litter", "deodorize", "cleaning_start"), main)
        assertEquals("pause", more.first())
    }

    @Test
    fun `a fallback is never twice in view, and a device with nothing left to offer keeps fewer slots`() {
        val two = listOf(action("feed"), action("play_sound"))

        assertEquals(
            listOf("play_sound", "feed") to emptyList<String>(),
            layout(listOf("play_sound", "gone_1", "gone_2"), two),
        )
    }

    @Test
    fun `fewer than three chosen shows fewer, none chosen shows none, and a choice is cut to three`() {
        assertEquals(listOf("deodorize"), layout(listOf("deodorize")).first)
        assertEquals(emptyList<String>(), layout(emptyList()).first)
        assertEquals(t4.size, layout(emptyList()).second.size)
        assertEquals(
            listOf("pause", "resume", "reset"),
            layout(listOf("pause", "pause", "resume", "reset", "deodorize")).first,
        )
    }

    @Test
    fun `no choice is the app's own layout`() {
        assertEquals(DeviceActionOrder.layout(t4), DeviceActionOrder.layout(t4, pinned = null))
    }

    @Test
    fun `the row's rules - add up to three, remove, move within bounds`() {
        val row = listOf("a", "b")

        assertEquals(listOf("a", "b", "c"), QuickActionRow.apply(row, QuickActionEdit.Add("c")))
        assertEquals(row, QuickActionRow.apply(row, QuickActionEdit.Add("a")))
        assertNull(QuickActionRow.apply(listOf("a", "b", "c"), QuickActionEdit.Add("d")))
        assertEquals(listOf("b"), QuickActionRow.apply(row, QuickActionEdit.Remove("a")))
        assertEquals(listOf("b", "a"), QuickActionRow.apply(row, QuickActionEdit.Move("b", -1)))
        assertEquals(listOf("b", "a"), QuickActionRow.apply(row, QuickActionEdit.Move("a", +1)))
        assertEquals(row, QuickActionRow.apply(row, QuickActionEdit.Move("a", -1)))
        assertEquals(row, QuickActionRow.apply(row, QuickActionEdit.Move("b", +1)))
        assertEquals(row, QuickActionRow.apply(row, QuickActionEdit.Move("z", +1)))
    }

    /** The phone's store, in memory. */
    private class InMemoryQuickActions : QuickActionsRepository {
        val stored = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
        var failing = false

        override fun observe(deviceId: Long): Flow<List<String>?> = stored.map { it[deviceId] }

        override suspend fun update(deviceId: Long, transform: (List<String>?) -> List<String>?): Boolean {
            if (failing) return false
            val next = transform(stored.value[deviceId])
            stored.value = if (next == null) stored.value - deviceId else stored.value + (deviceId to next)
            return true
        }
    }

    @Test
    fun `the first edit turns the app's row into the user's, and reset gives it back`() = runTest {
        val repository = InMemoryQuickActions()
        val edit = EditQuickActionsUseCase(repository)
        val observe = ObserveQuickActionsUseCase(repository)

        assertNull(observe(T4).first())

        assertEquals(QuickActionEditResult.Saved, edit(T4, t4, QuickActionEdit.Move("level_litter", -1)))
        assertEquals(listOf("cleaning_start", "level_litter", "deodorize"), observe(T4).first())

        assertEquals(QuickActionEditResult.RowFull, edit(T4, t4, QuickActionEdit.Add("dump_litter")))
        assertEquals(listOf("cleaning_start", "level_litter", "deodorize"), observe(T4).first())

        edit(T4, t4, QuickActionEdit.Remove("deodorize"))
        edit(T4, t4, QuickActionEdit.Add("dump_litter"))
        assertEquals(listOf("cleaning_start", "level_litter", "dump_litter"), observe(T4).first())
        // Another device keeps the app's choice.
        assertNull(observe(OTHER).first())

        ResetQuickActionsUseCase(repository)(T4)
        assertNull(observe(T4).first())
    }

    @Test
    fun `an edit applies to the row as shown, where a vanished action already gave way`() = runTest {
        val repository = InMemoryQuickActions()
        repository.stored.value = mapOf(T4 to listOf("pause", "vanished", "resume"))

        EditQuickActionsUseCase(repository)(T4, t4, QuickActionEdit.Move("resume", -1))

        assertEquals(listOf("pause", "resume", "cleaning_start"), repository.stored.value[T4])
    }

    @Test
    fun `adding to a full row of the app's choice stores nothing, and a failed write says so`() = runTest {
        val repository = InMemoryQuickActions()
        val edit = EditQuickActionsUseCase(repository)

        assertEquals(QuickActionEditResult.RowFull, edit(T4, t4, QuickActionEdit.Add("pause")))
        assertNull(repository.stored.value[T4])

        repository.failing = true
        assertEquals(QuickActionEditResult.NotSaved, edit(T4, t4, QuickActionEdit.Remove("deodorize")))
    }

    private companion object {
        const val T4 = 1001L
        const val OTHER = 2001L
    }
}
