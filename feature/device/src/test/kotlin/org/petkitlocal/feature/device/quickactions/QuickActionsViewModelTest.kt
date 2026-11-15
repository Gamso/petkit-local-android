package org.petkitlocal.feature.device.quickactions

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.device.EditQuickActionsUseCase
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveQuickActionsUseCase
import org.petkitlocal.core.domain.device.QuickActionEditResult
import org.petkitlocal.core.domain.device.ResetQuickActionsUseCase
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeQuickActionsRepository
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceFixtures

class QuickActionsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val litterBox = DeviceFixtures.litterBox
    private val devices = FakeDeviceRepository(listOf(litterBox, DeviceFixtures.fountain))
    private val quick = FakeQuickActionsRepository()

    private fun viewModel(
        id: Long = litterBox.id,
        saved: SavedStateHandle = SavedStateHandle(mapOf(DEVICE_ID_KEY to id)),
    ) = QuickActionsViewModel(
        saved,
        ObserveDeviceUseCase(devices),
        ObserveQuickActionsUseCase(quick),
        EditQuickActionsUseCase(quick),
        ResetQuickActionsUseCase(quick),
    )

    /** The host collects the state, as the destination does. */
    private fun TestScope.shown(viewModel: QuickActionsViewModel) =
        backgroundScope.launch { viewModel.uiState.collect {} }

    private val QuickActionsViewModel.editor get() = checkNotNull(uiState.value.editor)

    private fun List<DeviceAction>.keys() = map { it.key }

    @Test
    fun `the app's row until the user chooses, read before the sheet shows it`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.loaded)

        shown(viewModel)

        with(viewModel.uiState.value) {
            assertTrue(loaded)
            assertNull(pinned)
            assertNull(editor)
        }
        viewModel.onOpen()
        with(viewModel.editor) {
            assertEquals("Pura Max", deviceName)
            assertEquals(listOf("cleaning_start", "deodorize", "level_litter"), row.keys())
            assertEquals(litterBox.actions.size - 3, others.size)
            assertFalse(custom)
        }
    }

    @Test
    fun `choosing - remove one, add a destructive one, and the sheet's keys follow`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        viewModel.onOpen()

        viewModel.onRemove("deodorize")
        viewModel.onAdd("dump_litter")

        assertEquals(listOf("cleaning_start", "level_litter", "dump_litter"), viewModel.uiState.value.pinned)
        with(viewModel.editor) {
            assertTrue(custom)
            assertEquals(listOf("cleaning_start", "level_litter", "dump_litter"), row.keys())
            assertTrue(row.last().destructive)
            assertTrue("deodorize" in others.keys())
            assertFalse("dump_litter" in others.keys())
        }
        // Another device is not touched.
        assertNull(quick.stored.value[DeviceFixtures.fountain.id])
    }

    @Test
    fun `reordering moves one slot at a time, never past either end`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        viewModel.onOpen()

        viewModel.onMoveUp("level_litter")
        assertEquals(listOf("cleaning_start", "level_litter", "deodorize"), viewModel.editor.row.keys())
        viewModel.onMoveUp("level_litter")
        viewModel.onMoveUp("level_litter")
        assertEquals(listOf("level_litter", "cleaning_start", "deodorize"), viewModel.editor.row.keys())
        viewModel.onMoveDown("cleaning_start")
        assertEquals(listOf("level_litter", "deodorize", "cleaning_start"), viewModel.editor.row.keys())
    }

    @Test
    fun `adding to a full row says so where the row is, and the next change clears it`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        viewModel.onOpen()

        viewModel.onAdd("pause")

        assertEquals(QuickActionEditResult.RowFull, viewModel.editor.notice)
        assertNull(viewModel.uiState.value.pinned)

        viewModel.onRemove("deodorize")
        assertNull(viewModel.editor.notice)
    }

    @Test
    fun `reset gives the row back to the app`() = runTest(main.dispatcher) {
        quick.stored.value = mapOf(litterBox.id to listOf("pause"))
        val viewModel = viewModel()
        shown(viewModel)
        viewModel.onOpen()
        assertTrue(viewModel.editor.custom)

        viewModel.onReset()

        assertNull(viewModel.uiState.value.pinned)
        assertFalse(viewModel.editor.custom)
        assertEquals(listOf("cleaning_start", "deodorize", "level_litter"), viewModel.editor.row.keys())
    }

    @Test
    fun `an action the server stopped listing drops out and its slot falls back, then edits start from that`() =
        runTest(main.dispatcher) {
            quick.stored.value = mapOf(litterBox.id to listOf("pause", "deodorize", "resume"))
            val viewModel = viewModel()
            shown(viewModel)
            viewModel.onOpen()
            assertEquals(listOf("pause", "deodorize", "resume"), viewModel.editor.row.keys())

            devices.devices.value =
                listOf(litterBox.copy(actions = litterBox.actions.filterNot { it.key == "deodorize" }))

            assertEquals(listOf("pause", "cleaning_start", "resume"), viewModel.editor.row.keys())
            assertFalse("deodorize" in viewModel.editor.others.keys())

            viewModel.onMoveUp("resume")
            assertEquals(listOf("pause", "resume", "cleaning_start"), viewModel.uiState.value.pinned)
        }

    @Test
    fun `a change the phone cannot save is said, and nothing changed`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        shown(viewModel)
        viewModel.onOpen()
        quick.failing = true

        viewModel.onRemove("deodorize")

        assertEquals(QuickActionEditResult.NotSaved, viewModel.editor.notice)
        assertEquals(listOf("cleaning_start", "deodorize", "level_litter"), viewModel.editor.row.keys())
    }

    @Test
    fun `closing keeps the choice, and an open editor survives the process through the saved state`() =
        runTest(main.dispatcher) {
            val saved = SavedStateHandle(mapOf(DEVICE_ID_KEY to litterBox.id))
            val first = viewModel(saved = saved)
            shown(first)
            first.onOpen()
            first.onRemove("level_litter")

            val restored = viewModel(saved = saved)
            shown(restored)
            assertNotNull(restored.uiState.value.editor)

            restored.onClose()
            assertNull(restored.uiState.value.editor)
            assertEquals(listOf("cleaning_start", "deodorize"), restored.uiState.value.pinned)
        }

    @Test
    fun `no editor for a device that is gone`() = runTest(main.dispatcher) {
        val viewModel = viewModel(id = 9999)
        shown(viewModel)

        viewModel.onOpen()
        viewModel.onAdd("feed")

        assertNull(viewModel.uiState.value.editor)
        assertTrue(quick.stored.value.isEmpty())
    }
}
