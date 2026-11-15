package org.petkitlocal.core.domain.device

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue

class SettingCatalogTest {
    /**
     * Every settable key of the mock's catalog (mock-server/src/main/resources/catalog.json,
     * generated from petkit-local's entity definitions), read without a JSON library: each entity
     * object has its `key` before its `settable`.
     */
    private val catalogSettableKeys: Set<String> by lazy {
        val text = File(checkNotNull(System.getProperty("petkit.catalog"))).readText()
        Regex(""""key":\s*"([a-z0-9_]+)"[^{}]*?"settable":\s*true""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()
    }

    /** Settable keys of the catalog this app shows in Other, on purpose. None today. */
    private val knowinglyOther = emptySet<String>()

    @Test
    fun `every settable key of the catalog has a category`() {
        assertTrue(catalogSettableKeys.size > 90, "read ${catalogSettableKeys.size} keys")
        val placed = SettingCatalog.KNOWN_KEYS + knowinglyOther

        assertEquals(emptySet<String>(), catalogSettableKeys - placed)
    }

    @Test
    fun `the table names no key the catalog does not have`() {
        assertEquals(emptySet<String>(), SettingCatalog.KNOWN_KEYS - catalogSettableKeys)
        assertEquals(emptySet<String>(), SettingCatalog.UNEXPLAINED - SettingCatalog.KNOWN_KEYS)
    }

    @Test
    fun `an unknown key goes to Other, or to Diagnostics when the server says so`() {
        assertEquals(SettingCategory.Other, SettingCatalog.category(entity("brand_new")))
        assertEquals(
            SettingCategory.Diagnostics,
            SettingCatalog.category(entity("debug_log", EntityCategory.Diagnostic)),
        )
        assertNull(SettingCatalog.explanationKey(entity("brand_new"), DeviceFamily.Other))
    }

    @Test
    fun `explanations are given where known, and per family where one key means two things`() {
        assertEquals("auto_work", SettingCatalog.explanationKey(entity("auto_work"), DeviceFamily.LitterBox))
        assertNull(SettingCatalog.explanationKey(entity("downpos"), DeviceFamily.LitterBox))
        assertEquals(
            "disturb_mode_litter",
            SettingCatalog.explanationKey(entity("disturb_mode"), DeviceFamily.LitterBox),
        )
        assertNull(SettingCatalog.explanationKey(entity("disturb_mode"), DeviceFamily.Feeder))
    }

    @Test
    fun `a litter box's settings come in the owner's order`() {
        val keys = listOf("auto_work", "sand_type", "kitten", "display", "cleaning_schedule", "camera")

        assertEquals(
            listOf(
                SettingCategory.Cleaning,
                SettingCategory.Litter,
                SettingCategory.ComfortAndSafety,
                SettingCategory.Camera,
                SettingCategory.DisplayAndQuiet,
                SettingCategory.Advanced,
            ),
            keys.map { SettingCatalog.category(entity(it)) }.sorted(),
        )
        assertTrue(SettingCategory.Advanced.folded && SettingCategory.Diagnostics.folded)
    }

    private fun entity(key: String, category: EntityCategory? = null) =
        Entity(key, key, EntityComponent.Switch, EntityValue.Bool(false), category = category, settable = true)
}
