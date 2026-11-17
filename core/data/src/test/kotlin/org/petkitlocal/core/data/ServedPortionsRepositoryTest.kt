package org.petkitlocal.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Portions
import org.robolectric.RobolectricTestRunner

/**
 * Integration: a meal's portions are read once from its event's fields, against a scripted server
 * and a real Room database, and kept there.
 */
@RunWith(RobolectricTestRunner::class)
class ServedPortionsRepositoryTest {
    @get:Rule
    val server = ScriptedServer()

    private val portions by lazy { ServedPortionsRepositoryImpl(server.api, server.db.timelineDao()) }

    @Test
    fun `a dual hopper's meal gives its count per hopper, a single hopper's its amount`() = runTest {
        server.on(
            "timeline/31",
            event(
                31,
                """{"key":"real_amount1","label":"real_amount1","text":"1"},""" +
                    """{"key":"real_amount2","label":"real_amount2","text":"2"}""",
            ),
        )
        server.on("timeline/30", event(30, """{"key":"real_amount","label":"real_amount","text":"3"}"""))

        assertEquals(Outcome.Success(Unit), portions.fetch(setOf(30, 31)))

        assertEquals(
            mapOf(30L to Portions.Single(3), 31L to Portions.Dual(1, 2)),
            portions.observe(setOf(30, 31)).first(),
        )
    }

    @Test
    fun `a meal is read once, and one whose event says nothing is not read again`() = runTest {
        server.on("timeline/30", event(30, """{"key":"result","label":"Result","text":"completed"}"""))
        portions.fetch(setOf(30))

        portions.fetch(setOf(30))

        assertEquals(listOf("timeline/30"), server.requests)
        assertEquals(mapOf(30L to null), portions.observe(setOf(30)).first())
    }

    @Test
    fun `a skipped run is no portion, and an event gone from the server is not asked again`() = runTest {
        server.on("timeline/30", event(30, """{"key":"real_amount","label":"real_amount","text":"-1"}"""))
        server.on("timeline/31", """{"error":"event_not_found","message":"no such event"}""", code = 404)

        assertEquals(Outcome.Success(Unit), portions.fetch(setOf(30, 31)))
        portions.fetch(setOf(31))

        assertEquals(mapOf(30L to Portions.Single(0), 31L to null), portions.observe(setOf(30, 31)).first())
        assertEquals(listOf("timeline/30", "timeline/31"), server.requests)
    }

    @Test
    fun `a failure stops the reading and keeps nothing for that meal`() = runTest {
        server.on("timeline/30", """{"error":"unavailable","message":"x"}""", code = 503)

        val result = portions.fetch(setOf(30, 31))

        assertTrue((result as Outcome.Failure).error is DomainError.Server)
        assertEquals(listOf("timeline/30"), server.requests)
        assertEquals(emptyMap<Long, Portions?>(), portions.observe(setOf(30, 31)).first())
    }

    private fun event(id: Long, fields: String) = """{"event":{"id":$id,"device_id":2004,"device_type":"d4sh",
        "ts":1794719959,"event_type":"4","event_kind":"feeding","label":"Feeding completed","pet":null,
        "facts":[],"fields":[$fields],"media":null}}"""
}
