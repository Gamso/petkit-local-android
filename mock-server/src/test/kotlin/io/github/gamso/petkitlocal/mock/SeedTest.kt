package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.api.AccessoryRef
import io.github.gamso.petkitlocal.mock.catalog.Catalog
import io.github.gamso.petkitlocal.mock.seed.Seed
import io.github.gamso.petkitlocal.mock.state.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeedTest {
    private val world = World(testConfig())

    @Test
    fun `every family of petkit-local is seeded`() {
        assertEquals(
            listOf("t3", "t4", "t5", "t6", "t7", "d4", "d4s", "d4h", "d4sh", "w7h", "w5", "ctw3", "k3"),
            world.devices.values.map { it.type },
        )
        assertEquals(
            setOf("litter_box", "feeder", "fountain", "purifier"),
            world.devices.values
                .map { it.model.kind }
                .toSet(),
        )
        assertEquals(
            listOf("t5", "t6", "t7", "d4h", "d4sh", "w7h"),
            world.devices.values
                .filter { it.model.isCamera }
                .map { it.type },
        )
        assertEquals(
            "Pura Max",
            world.devices
                .getValue(Seed.T4)
                .model.model,
        )
    }

    @Test
    fun `the second generations are named by it, and a device of unknown generation by its codename`() {
        val summaries = world.devices.values.associate { it.id to it.summary() }
        val named = summaries.getValue(Seed.T4)
        assertEquals(listOf("Pura Max", "Pura Max 2", 2), listOf(named.model, named.modelName, named.generation))
        val dual = summaries.getValue(Seed.D4SH)
        assertEquals(
            listOf("YumShare Dual-Hopper", "YumShare Dual-Hopper 2", 2),
            listOf(dual.model, dual.modelName, dual.generation),
        )
        // Generation unknown, a one-product codename, an accessory: no generation, the model's name.
        listOf(Seed.D4H, Seed.T5, Seed.K3).map(summaries::getValue).forEach {
            assertEquals(null, it.generation, it.type)
            assertEquals(it.model, it.modelName, it.type)
        }
        assertEquals("YumShare Solo", summaries.getValue(Seed.D4H).modelName)
        // The detail says the same, and a generation the server does not name is not reported.
        assertEquals(
            "Pura Max 2",
            world.devices
                .getValue(Seed.T4)
                .detail()
                .modelName,
        )
        val unnamed =
            world.devices
                .getValue(Seed.D4H)
                .apply { generation = 3 }
                .summary()
        assertEquals(listOf("YumShare Solo", null), listOf(unnamed.modelName, unnamed.generation))
    }

    @Test
    fun `each device shows exactly the entities and actions petkit-local defines for its model`() {
        val catalog = Catalog.load()
        assertEquals("9e00c402df8a023eeb176e65fc5672c4451ed5bc", catalog.source)
        world.devices.values.forEach { d ->
            val detail = d.detail()
            // A T4 relaying a K3 also offers the K3's light, which the catalog has no rule for.
            val linked = if (d.hasK3Light) listOf("k3_light") else emptyList()
            assertEquals(catalog.model(d.type).entities.map { it.key } + linked, detail.entities.map { it.key }, d.type)
            assertEquals(catalog.model(d.type).actions.map { it.key }, detail.actions.map { it.key }, d.type)
        }
        val t4 = world.devices.getValue(Seed.T4).detail()
        assertTrue(t4.actions.map { it.key }.containsAll(listOf("cleaning_start", "dump_litter", "deodorize", "level_litter", "reset_n50")))
        assertTrue(
            t4.entities.map { it.key }.containsAll(listOf("device_status", "litter_percent", "waste_bin", "auto_work", "cleaning_delay")),
        )
        assertTrue(world.devices.getValue(Seed.T4).hasK3Light, "the T4 relays the K3")
    }

    @Test
    fun `Bluetooth accessories are devices that name their parent, which lists them`() {
        val summaries = world.devices.values.associate { it.id to it.summary() }
        assertEquals(
            mapOf(Seed.W5 to Seed.D4SH, Seed.CTW3 to Seed.D4, Seed.K3 to Seed.T4),
            summaries.values.filter { it.connection == "ble" }.associate { it.id to it.parentId },
        )
        val t4 = summaries.getValue(Seed.T4)
        assertEquals(listOf(AccessoryRef(Seed.K3, "k3", "purifier")), t4.accessories)
        assertEquals("wifi", t4.connection)
        assertEquals(null, t4.parentId)
        summaries.values.filter { it.connection == "ble" }.forEach {
            assertEquals(emptyList<AccessoryRef>(), it.accessories)
            assertEquals(null, it.firmware)
            // An accessory is online, and on a transport, as its parent is.
            val parent = summaries.getValue(it.parentId!!)
            assertEquals(parent.online, it.online)
            assertEquals(parent.transport, it.transport)
        }
        assertEquals(null, summaries.getValue(Seed.K3).lastSeen)
    }

    @Test
    fun `settable entities have a value and numbers carry bounds`() {
        world.devices.values.forEach { d ->
            d.detail().entities.filter { it.settable }.forEach { e ->
                assertTrue(e.value != null, "${d.type}.${e.key} has no value")
                if (e.component == "number") assertTrue(e.min != null && e.max != null && e.step != null, "${d.type}.${e.key}")
                if (e.component == "select") assertTrue(e.display in e.options.orEmpty(), "${d.type}.${e.key} display ${e.display}")
            }
        }
    }

    @Test
    fun `two pets, one heavier, attributed to the devices that see them`() {
        val (mochi, tigrou) = world.pets.values.toList()
        assertTrue(tigrou.weightG > mochi.weightG + 1000)
        assertTrue(mochi.weightLearned)
        assertTrue(Seed.T4 in mochi.deviceIds && Seed.T4 in tigrou.deviceIds)
        val visits =
            world.timeline.cards.values
                .filter { it.kind == "visit" && it.petId != null }
        val byPet = visits.groupBy { it.petId!! }.mapValues { (_, v) -> v.mapNotNull { it.weightG }.average() }
        assertTrue(byPet.getValue(2) > byPet.getValue(1) + 1000)
    }

    @Test
    fun `history is deterministic for one seed and differs for another`() {
        fun shape(w: World) = w.timeline.sorted().map { listOf(it.kind, it.deviceId, it.petId, it.weightG, it.durationSec, it.label) }
        assertEquals(shape(world), shape(World(testConfig())))
        assertNotEquals(shape(world), shape(World(testConfig().copy(seed = 7))))
    }

    @Test
    fun `history covers every family and every filter chip`() {
        val cards = world.timeline.cards.values
        val chips = cards.flatMap { it.filters }.toSet()
        assertEquals(setOf("pet", "toileting", "drinking", "feeding", "cleaning", "health_alert", "fault"), chips)
        val devicesWithHistory = cards.map { it.deviceId }.toSet()
        listOf(Seed.T3, Seed.T4, Seed.T5, Seed.T6, Seed.T7, Seed.D4, Seed.D4S, Seed.D4H, Seed.D4SH, Seed.W7H).forEach {
            assertTrue(it in devicesWithHistory, "no history for $it")
        }
    }
}
