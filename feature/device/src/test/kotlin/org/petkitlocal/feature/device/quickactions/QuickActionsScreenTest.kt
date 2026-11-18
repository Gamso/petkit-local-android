package org.petkitlocal.feature.device.quickactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.device.EditQuickActionsUseCase
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveQuickActionsUseCase
import org.petkitlocal.core.domain.device.QuickActionEditResult
import org.petkitlocal.core.domain.device.ResetQuickActionsUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeQuickActionsRepository
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.LocalQuickActions
import org.petkitlocal.feature.device.common.QuickActions
import org.petkitlocal.feature.device.common.QuickActionsTestTags
import org.petkitlocal.feature.device.generic.GenericDeviceActions
import org.petkitlocal.feature.device.generic.GenericDeviceScreen
import org.petkitlocal.feature.device.litter.LitterCallbacks
import org.petkitlocal.feature.device.litter.LitterFixtures
import org.petkitlocal.feature.device.litter.LitterScreen
import org.petkitlocal.feature.device.litter.PageCommandCallbacks
import org.petkitlocal.feature.device.settings.DeviceSettingsActions
import org.petkitlocal.feature.device.settings.DeviceSettingsScreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests (Robolectric) of the quick actions (issue #40): the editor and its TalkBack
 * actions, the two ways in (the expanded sheet, the settings), the row the sheet then shows on
 * the generic and litter box pages, and the whole loop through the real ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class QuickActionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val callbacks = QuickActionsCallbacks(
        onClose = { calls += "close" },
        onAdd = { calls += "add:$it" },
        onRemove = { calls += "remove:$it" },
        onMoveUp = { calls += "up:$it" },
        onMoveDown = { calls += "down:$it" },
        onReset = { calls += "reset" },
    )

    private val fountain = DeviceFixtures.fountain

    private fun editor(pinned: List<String>?, notice: QuickActionEditResult? = null): QuickActionsEditorState {
        val layout = DeviceActionOrder.layout(fountain.actions, pinned)
        return QuickActionsEditorState(fountain.modelName, layout.main, layout.more, pinned != null, notice)
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { PkTheme { content() } }
    }

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val actions = fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == label }.action() }
    }

    private fun SemanticsNodeInteraction.customActionLabels(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    private fun SemanticsNodeInteraction.stateDescription(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun `each action in view reads its position and offers move and remove to TalkBack`() {
        show { QuickActionsEditor(editor(listOf("fountain_refill", "fountain_flush", "fountain_drain")), callbacks) }

        val first = compose.onNodeWithTag(QuickActionsTestTags.inView("fountain_refill"))
        val middle = compose.onNodeWithTag(QuickActionsTestTags.inView("fountain_flush"))
        val last = compose.onNodeWithTag(QuickActionsTestTags.inView("fountain_drain"))
        assertEquals("1 of 3", first.stateDescription())
        assertEquals(listOf("Move down", "Remove from quick actions"), first.customActionLabels())
        assertEquals(listOf("Move up", "Move down", "Remove from quick actions"), middle.customActionLabels())
        assertEquals(listOf("Move up", "Remove from quick actions"), last.customActionLabels())

        middle.customAction("Move up")
        last.customAction("Remove from quick actions")
        first.customAction("Move down")

        assertEquals(listOf("up:fountain_flush", "remove:fountain_drain", "down:fountain_refill"), calls)
    }

    @Test
    fun `the arrows and the remove button are buttons naming the action`() {
        show { QuickActionsEditor(editor(listOf("fountain_refill", "fountain_flush")), callbacks) }

        compose.onNodeWithContentDescription("Move Refill down").performClick()
        compose.onNodeWithContentDescription("Move Drain & Flush up").performClick()
        compose.onNodeWithContentDescription("Remove Refill from quick actions").performClick()
        // No way up from the top, or down from the bottom.
        compose.onNodeWithContentDescription("Move Refill up").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move Drain & Flush down").assertDoesNotExist()

        assertEquals(listOf("down:fountain_refill", "up:fountain_flush", "remove:fountain_refill"), calls)
    }

    @Test
    fun `the app's row says it is the app's, offers no reset, and an action under More is a tap away`() {
        show { QuickActionsEditor(editor(pinned = null), callbacks) }

        compose.onNodeWithText("Chosen by the app until you change them.").assertExists()
        compose.onNodeWithText("Always in view (2 of 3)").assertExists()
        compose.onNodeWithTag(QuickActionsTestTags.RESET).assertDoesNotExist()
        val drain = compose.onNodeWithTag(QuickActionsTestTags.other("fountain_drain"))
        drain.assert(hasAnyText("Asks for confirmation first"))
        assertEquals("Add to quick actions", drain.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        drain.performClick()
        compose.onNodeWithTag(QuickActionsTestTags.DONE).performScrollTo().performClick()

        assertEquals(listOf("add:fountain_drain", "close"), calls)
    }

    @Test
    fun `a full row is said under the row, never by greying Add out, and a chosen row can be reset`() {
        show {
            QuickActionsEditor(
                editor(
                    listOf("fountain_refill", "fountain_flush", "fountain_reset_circulation_pump"),
                    QuickActionEditResult.RowFull,
                ),
                callbacks,
            )
        }

        compose.onNodeWithText("Three actions already: remove one before adding another.").assertExists()
        compose.onNodeWithTag(QuickActionsTestTags.other("fountain_drain")).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.RESET).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("add:fountain_drain", "reset", "close"), calls)
    }

    @Test
    fun `an empty row says only More shows`() {
        show { QuickActionsEditor(editor(emptyList()), callbacks) }

        compose.onNodeWithText("None: only More shows at the bottom of the page.").assertExists()
        compose.onNodeWithText("Always in view (0 of 3)").assertExists()
    }

    private fun generic(quick: QuickActions, expanded: Boolean = false) = show {
        CompositionLocalProvider(LocalQuickActions provides quick) {
            GenericDeviceScreen(
                DeviceFixtures.generic(),
                GenericDeviceActions(onAction = { calls += "action:${it.key}" }),
                initiallyExpanded = expanded,
            )
        }
    }

    private val inSheet = hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET))

    @Test
    fun `a pinned destructive action is in the collapsed row, and pressing it goes to the confirmation`() {
        generic(QuickActions(pinned = listOf("fountain_drain", "fountain_refill")))

        compose.onNode(hasText("Drain") and inSheet).assertIsDisplayed().performClick()
        // Drain & Flush was not chosen: under More only.
        compose.onNode(hasText("Drain & Flush") and inSheet).assertDoesNotExist()

        assertEquals(listOf("action:fountain_drain"), calls)
    }

    @Test
    fun `the expanded sheet leads to the editor and folds itself away`() {
        generic(QuickActions(onEdit = { calls += "edit" }), expanded = true)

        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).assertIsDisplayed().performClick()

        assertEquals(listOf("edit"), calls)
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).assertDoesNotExist()
    }

    @Test
    fun `More stays when every action is in view, so the editor can still be reached`() {
        generic(QuickActions(pinned = listOf("fountain_refill", "fountain_flush", "fountain_drain"), onEdit = {}))

        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).assertIsDisplayed()
    }

    @Test
    fun `the sheet waits for the stored choice instead of showing the app's row first`() {
        generic(QuickActions(ready = false))

        compose.onNodeWithTag(DeviceTestTags.ACTION_SHEET).assertDoesNotExist()
    }

    @Test
    fun `the settings lead to the editor and say which actions are in view`() {
        show {
            CompositionLocalProvider(
                LocalQuickActions provides QuickActions(pinned = listOf("dump_litter"), onEdit = {
                    calls += "edit"
                }),
            ) {
                DeviceSettingsScreen(
                    DeviceFixtures.settings(device = DeviceFixtures.litterBox),
                    DeviceSettingsActions(),
                )
            }
        }

        compose.onNodeWithTag(DeviceTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(QuickActionsTestTags.SETTINGS_ROW))
        compose.onNodeWithText("On this phone").assertExists()
        compose.onNodeWithTag(QuickActionsTestTags.SETTINGS_ROW).assert(hasAnyText("Dump Litter")).performClick()

        assertEquals(listOf("edit"), calls)
    }

    @Test
    fun `a fountain whose sheet shows its mode offers no quick actions in its settings`() {
        // The W7H's flow mode is settable: its sheet's row is the mode, not the user's actions.
        show {
            CompositionLocalProvider(LocalQuickActions provides QuickActions(onEdit = { calls += "edit" })) {
                DeviceSettingsScreen(DeviceFixtures.settings(), DeviceSettingsActions())
            }
        }

        compose.onNodeWithTag(QuickActionsTestTags.SETTINGS_ROW).assertDoesNotExist()
        compose.onNodeWithText("On this phone").assertDoesNotExist()
    }

    @Test
    fun `the litter box page shows the user's row through its own scaffold`() {
        show {
            CompositionLocalProvider(
                LocalQuickActions provides QuickActions(pinned = listOf("dump_litter", "level_litter")),
            ) {
                LitterScreen(
                    LitterFixtures.content,
                    DeviceNavigation(),
                    LitterCallbacks(commands = PageCommandCallbacks(onAction = { calls += "action:${it.key}" })),
                )
            }
        }

        compose.onNode(hasText("Dump Litter") and inSheet).assertIsDisplayed().performClick()

        assertEquals(listOf("action:dump_litter"), calls)
    }

    @Test
    fun `pin an action from the sheet, through the real ViewModel, and see it in the collapsed row`() {
        val devices = FakeDeviceRepository(listOf(fountain))
        val quick = FakeQuickActionsRepository()
        val viewModel = QuickActionsViewModel(
            SavedStateHandle(mapOf(DEVICE_ID_KEY to fountain.id)),
            ObserveDeviceUseCase(devices),
            ObserveQuickActionsUseCase(quick),
            EditQuickActionsUseCase(quick),
            ResetQuickActionsUseCase(quick),
        )
        show {
            QuickActionsHost(viewModel) {
                GenericDeviceScreen(DeviceFixtures.generic(), GenericDeviceActions())
            }
        }
        compose.onNode(hasText("Drain") and inSheet).assertDoesNotExist()

        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.other("fountain_drain")).performClick()
        compose.onNodeWithTag(QuickActionsTestTags.inView("fountain_drain")).customAction("Move up")
        compose.onNodeWithTag(QuickActionsTestTags.DONE).performScrollTo().performClick()

        compose.onNodeWithTag(QuickActionsTestTags.EDITOR).assertDoesNotExist()
        compose.onNode(hasText("Drain") and inSheet).assertIsDisplayed()
        assertEquals(
            listOf("fountain_refill", "fountain_drain", "fountain_flush"),
            runBlocking { quick.observe(fountain.id).first() },
        )
    }
}

/** [text] on the node itself (merged) or on one of its children. */
private fun hasAnyText(text: String) =
    hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))
