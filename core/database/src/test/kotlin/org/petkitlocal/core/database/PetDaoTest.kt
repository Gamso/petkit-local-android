package org.petkitlocal.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.database.model.PetActivityRow
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.database.model.PetWeightRow
import org.robolectric.RobolectricTestRunner

/** Integration: what the cache holds of a pet beyond its row, on a real (in-memory) SQLite database. */
@RunWith(RobolectricTestRunner::class)
class PetDaoTest {
    private lateinit var db: PetkitDatabase
    private val dao get() = db.petDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PetkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `one pet read alone keeps its place in the list, or goes after the others`() = runTest {
        dao.replaceAll(listOf(pet(1, "Mochi", 0), pet(2, "Tigrou", 1)))

        dao.writePet(pet(1, "Mochi-chan", position = 0), activity(1))
        dao.writePet(pet(3, "Nala", position = 0), activity(3))

        assertEquals(listOf("Mochi-chan", "Tigrou", "Nala"), dao.observePets().first().map { it.name })
        assertEquals(listOf(0, 1, 2), dao.observePets().first().map { it.position })
    }

    @Test
    fun `a pet the list no longer has takes its activity and weights with it`() = runTest {
        dao.replaceAll(listOf(pet(1, "Mochi", 0), pet(2, "Tigrou", 1)))
        dao.writePet(pet(2, "Tigrou", 1), activity(2))
        dao.replaceWeights(2, 0.0, listOf(weight(10, 2, 100.0)))
        dao.writePet(pet(1, "Mochi", 0), activity(1))
        dao.replaceWeights(1, 0.0, listOf(weight(11, 1, 100.0)))

        dao.replaceAll(listOf(pet(1, "Mochi", 0)))

        assertNull(dao.observeActivity(2).first())
        assertEquals(emptyList<PetWeightRow>(), dao.observeWeights(2, 0.0).first())
        assertEquals(1L, dao.observeActivity(1).first()?.petId)
        assertEquals(listOf(11L), dao.observeWeights(1, 0.0).first().map { it.visitId })
    }

    @Test
    fun `weights are replaced from a time on, oldest first, and older ones stay`() = runTest {
        dao.replaceWeights(1, 0.0, listOf(weight(1, 1, 100.0), weight(2, 1, 200.0), weight(3, 1, 300.0)))

        dao.replaceWeights(1, 150.0, listOf(weight(4, 1, 400.0), weight(3, 1, 300.0)))

        assertEquals(listOf(1L, 3L, 4L), dao.observeWeights(1, 0.0).first().map { it.visitId })
        assertEquals(listOf(3L, 4L), dao.observeWeights(1, 250.0).first().map { it.visitId })
    }

    @Test
    fun `deleting a pet removes everything held of it`() = runTest {
        dao.writePet(pet(1, "Mochi", 0), activity(1))
        dao.replaceWeights(1, 0.0, listOf(weight(1, 1, 100.0)))

        dao.deletePet(1)

        assertEquals(emptyList<PetRow>(), dao.observePets().first())
        assertNull(dao.observeActivity(1).first())
        assertEquals(emptyList<PetWeightRow>(), dao.observeWeights(1, 0.0).first())
    }

    private fun pet(id: Long, name: String, position: Int) = PetRow(
        id = id,
        position = position,
        name = name,
        deviceIdsJson = "[]",
        enteredWeightG = 4200.0,
        weightG = null,
        weightLearned = false,
        photo = null,
    )

    private fun activity(petId: Long) = PetActivityRow(
        petId = petId,
        medianWeightG = 4300.0,
        visitsToday = 2,
        lastVisitTs = 1_000.0,
        lastVisitWeightG = 4300.0,
        lastVisitDurationSec = 90.0,
        lastDeviceId = 1002,
        recentVisitsJson = "[]",
        fetchedAt = 1_000_000,
    )

    private fun weight(visitId: Long, petId: Long, ts: Double) = PetWeightRow(visitId, petId, ts, weightG = 4300)
}
