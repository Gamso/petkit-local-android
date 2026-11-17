package org.petkitlocal.feature.device.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.device.DeviceSections
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.testing.Household
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless settings screen and its editors. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class DeviceSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val saved = mutableListOf<EntityValue>()

    private val actions = DeviceSettingsActions(
        onBack = { calls += "back" },
        onToggle = { key, on -> calls += "toggle:$key:$on" },
        onEdit = { calls += "edit:$it" },
        onSave = { key, value ->
            calls += "save:$key"
            saved += value
        },
        onDismissEditor = { calls += "dismiss" },
        onAction = { calls += "action:${it.key}" },
        onSerialCopied = { calls += "copied" },
    )

    private fun show(state: DeviceSettingsUiState) {
        compose.setContent { PkTheme { DeviceSettingsScreen(state, actions) } }
    }

    @Test
    fun `settings come by category, each with what it does, its control and its value`() {
        show(DeviceFixtures.settings())

        compose.onNodeWithText("Water flow").assertIsDisplayed()
        compose.onNodeWithText("In intermittent mode, how long the water runs.").assertIsDisplayed()
        compose.onNodeWithText("Locks the buttons on the device.").assertExists()
        compose.onNodeWithTag(DeviceTestTags.entity("child_lock")).assertIsOff()
        compose.onNodeWithText("3 min").assertIsDisplayed()
        compose.onNodeWithText("Continuous").assertIsDisplayed()
        compose.onNodeWithText("10:00 AM").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.SETTINGS_LIST).performScrollToNode(hasText("Display and quiet"))
        compose.onNodeWithText("22:00-07:00").assertExists()
    }

    @Test
    fun `a setting the app does not know is in Other, without explanation, and Advanced is folded`() {
        val unknown = DeviceFixtures.childLock.copy(key = "brand_new", name = "Brand New")
        val raw = DeviceFixtures.quietHours.copy(key = "cleaning_schedule", name = "Cleaning Schedule (JSON)")
        val device = DeviceFixtures.fountain.copy(entities = listOf(unknown, raw))
        show(
            DeviceFixtures.settings().copy(
                device = device,
                groups = DeviceSections.of(device).settings,
            ),
        )

        compose.onNodeWithText("Other").assertIsDisplayed()
        compose.onNodeWithText("Brand New").assertIsDisplayed()
        compose.onNodeWithText("Cleaning Schedule (JSON)").assertDoesNotExist()
        compose.onNodeWithText("Advanced (1)").performClick()
        compose.onNodeWithText("Cleaning Schedule (JSON)").assertIsDisplayed()
        compose.onNodeWithText("The cleaning schedule in its raw JSON form.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h2000dp")
    fun `in French`() {
        show(DeviceFixtures.settings())

        compose.onNodeWithText("Écoulement de l'eau").assertIsDisplayed()
        compose.onNodeWithText("Verrouille les boutons de l'appareil.").assertExists()
    }

    @Test
    fun `a switch is flipped in place, and any other setting opens its editor`() {
        show(DeviceFixtures.settings())

        compose.onNodeWithTag(DeviceTestTags.entity("child_lock")).performClick()
        compose.onNodeWithTag(DeviceTestTags.entity("fountain_time")).performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("toggle:child_lock:true", "edit:fountain_time", "back"), calls)
    }

    @Test
    fun `a switch being written cannot be flipped again`() {
        show(DeviceFixtures.settings(commands = CommandState(sending = setOf("child_lock"))))

        compose.onNodeWithTag(DeviceTestTags.entity("child_lock")).assertIsNotEnabled()
    }

    @Test
    fun `the number editor steps by the entity's step, and saves the value`() {
        show(DeviceFixtures.settings(editor = SettingEditorState(DeviceFixtures.fountainTime)))

        compose.onNodeWithText("Between 1 min and 60 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase").performClick()
        compose.onNodeWithContentDescription("Increase").performClick()
        compose.onNodeWithContentDescription("Decrease").performClick()
        compose.onNodeWithText("4 min").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.EDITOR_SAVE).performClick()

        assertEquals(listOf("save:fountain_time"), calls)
        assertEquals(listOf(EntityValue.Number(4.0)), saved)
    }

    @Test
    fun `what is wrong is said in the editor, and Save stays enabled`() {
        show(
            DeviceFixtures.settings(
                editor = SettingEditorState(DeviceFixtures.fountainTime, problem = EntityWriteProblem.AboveMax(60.0)),
            ),
        )

        compose.onNodeWithText("It must be at most 60 min.").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.EDITOR_SAVE).assertIsEnabled()
    }

    @Test
    fun `while saving, Save says so and waits`() {
        show(DeviceFixtures.settings(editor = SettingEditorState(DeviceFixtures.fountainTime, saving = true)))

        compose.onNodeWithText("Saving…").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.EDITOR_SAVE).assertIsNotEnabled()
    }

    @Test
    fun `a select sends the option picked`() {
        show(DeviceFixtures.settings(editor = SettingEditorState(DeviceFixtures.flowMode)))

        compose.onNodeWithText("Do not flow").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.option("intermittent")).performClick()

        assertEquals(listOf(EntityValue.Text("intermittent")), saved)
    }

    @Test
    fun `a time is sent as HH_MM`() {
        show(DeviceFixtures.settings(editor = SettingEditorState(DeviceFixtures.refillTime)))

        compose.onNodeWithTag(DeviceTestTags.EDITOR_SAVE).performClick()

        assertEquals(listOf(EntityValue.Text("10:00")), saved)
    }

    @Test
    fun `a text counts its characters, and the server's refusal is shown in place`() {
        val refusal = DomainError.Validation(null, "refused", "Quiet Refill Hours takes HH:MM-HH:MM")
        show(DeviceFixtures.settings(editor = SettingEditorState(DeviceFixtures.quietHours, error = refusal)))

        compose.onNodeWithText("11 / 255").assertIsDisplayed()
        compose.onNodeWithText("Quiet Refill Hours takes HH:MM-HH:MM").assertIsDisplayed()
        compose.onNode(hasText("22:00-07:00") and hasSetTextAction()).performTextReplacement("23:00-06:00")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag(DeviceTestTags.EDITOR_SAVE).performClick()

        assertEquals(listOf("dismiss", "save:refill_disturb_period"), calls)
        assertEquals(listOf(EntityValue.Text("23:00-06:00")), saved)
    }

    @Test
    fun `the identity card comes first, and Copy puts the serial number on the clipboard`() {
        show(DeviceFixtures.settings())

        compose.onNodeWithTag(DeviceTestTags.IDENTITY).assert(hasAnyText("EverSweet Ultra AI"))
        compose.onNode(hasText("Online") and hasAnyAncestor(hasTestTag(DeviceTestTags.IDENTITY))).assertIsDisplayed()
        compose.onNodeWithText("MQTT").assertIsDisplayed()
        compose.onNodeWithText("1.263").assertIsDisplayed()
        compose.onNodeWithText("20250610W7H00077").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy the serial number").performClick()
        compose.waitForIdle()

        assertEquals(listOf("copied"), calls)
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(
            ClipboardManager::class.java,
        )
        assertEquals("20250610W7H00077", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test
    fun `once copied, the button says so`() {
        show(DeviceFixtures.settings(serialCopied = true))

        compose.onNodeWithTag(DeviceTestTags.COPY_SERIAL).assert(hasAnyText("Copied"))
    }

    @Test
    fun `what the server does not send is left out, never shown as unknown`() {
        val bare = DeviceFixtures.fountain.copy(firmware = null, serial = null, transport = DeviceTransport.Unknown)
        show(DeviceFixtures.settings(device = bare.copy(online = false)))

        compose.onNode(hasText("Offline") and hasAnyAncestor(hasTestTag(DeviceTestTags.IDENTITY))).assertIsDisplayed()
        compose.onNodeWithText("Link").assertDoesNotExist()
        compose.onNodeWithText("Firmware").assertDoesNotExist()
        compose.onNodeWithText("Serial number").assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.COPY_SERIAL).assertDoesNotExist()
    }

    @Test
    fun `a litter box lists its parts and the N60 of its Pura Air, and resets its N50`() {
        show(DeviceFixtures.settings(device = DeviceFixtures.litterSettings, puraAir = DeviceFixtures.purifier))

        compose.onNodeWithText("Consumables").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.consumable("Litter")).assert(hasAnyText("91 %"))
        compose.onNodeWithTag(DeviceTestTags.consumable("WasteBin")).assert(hasAnyText("Full"))
        compose.onNodeWithTag(DeviceTestTags.consumable("Deodorizer")).assert(hasAnyText("21 days"))
        compose.onNodeWithTag(DeviceTestTags.consumable("PuraAirSpray"))
            .assert(hasAnyText("Pura Air · N60"))
            .assert(hasAnyText("Through the Pura Air K3"))
            .assert(hasAnyText("63 %"))
        // The page's other actions on a part stay on the page: only resets are here.
        compose.onNodeWithText("Level Litter").assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.consumableAction("reset_n50")).performClick()

        assertEquals(listOf("action:reset_n50"), calls)
    }

    @Test
    fun `a feeder lists its desiccant with its reset, and a reset in flight waits`() {
        val feeder = Household.feeder.copy(
            firmware = "1.267",
            serial = "20220914D4000654",
            actions = listOf(DeviceAction("reset_desiccant", "Reset Desiccant", destructive = true)),
        )
        show(DeviceFixtures.settings(device = feeder, commands = CommandState(sending = setOf("reset_desiccant"))))

        compose.onNodeWithTag(DeviceTestTags.consumable("Food")).assert(hasAnyText("OK"))
        compose.onNodeWithTag(DeviceTestTags.consumable("Desiccant")).assert(hasAnyText("3 days"))
        compose.onNodeWithTag(DeviceTestTags.consumableAction("reset_desiccant")).assertIsNotEnabled()
    }

    @Test
    fun `a relayed Bluetooth fountain names its relay, and has no firmware line`() {
        show(DeviceFixtures.settings(device = Household.batteryFountain, relay = Household.feeder))

        compose.onNodeWithText("Bluetooth, through the Fresh Element Solo").assertIsDisplayed()
        compose.onNodeWithText("Firmware").assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.consumable("Filter")).assert(hasAnyText("12 %"))
        compose.onNodeWithTag(DeviceTestTags.consumable("Water")).assertExists()
    }

    @Test
    fun `a Pura Air has its identity card and no consumables of its own`() {
        show(DeviceFixtures.settings(device = DeviceFixtures.purifier, relay = DeviceFixtures.puraMax))

        compose.onNodeWithText("Bluetooth, through the Pura Max").assertIsDisplayed()
        compose.onNodeWithText("K3-0001").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.CONSUMABLES).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h2000dp")
    fun `the identity card in French`() {
        show(DeviceFixtures.settings(device = DeviceFixtures.litterSettings, puraAir = DeviceFixtures.purifier))

        compose.onNodeWithText("En ligne").assertIsDisplayed()
        compose.onNodeWithText("N° de série").assertIsDisplayed()
        compose.onNodeWithText("Copier").assertIsDisplayed()
        compose.onNodeWithText("Consommables").assertIsDisplayed()
        compose.onNodeWithText("Via le Pura Air K3", substring = true).assertIsDisplayed()
    }
}

/** [text] on the node itself (merged) or on one of its children. */
private fun hasAnyText(text: String) =
    hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))
