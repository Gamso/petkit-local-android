package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.seed.Seed
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceWritesTest {
    private val entity = "/devices/{id}/entities/{key}"
    private val action = "/devices/{id}/actions/{action}"

    private suspend fun Api.set(
        device: Int,
        key: String,
        value: String,
    ) = put(entity, "/devices/$device/entities/$key", """{"value":$value}""")

    @Test
    fun `each component takes its own type and refuses the rest with 422 refused`() =
        mockTest { _, api ->
            api.login()
            // switch: a boolean, stored and answered as one
            val sw = api.set(Seed.T4, "child_lock", "true")
            assertEquals("queued", sw["delivered"].jsonPrimitive.content)
            assertEquals(
                true,
                sw["entity"]
                    .jsonObject["value"]!!
                    .jsonPrimitive.content
                    .toBoolean(),
            )
            assertEquals("refused", api.set(Seed.T4, "child_lock", "1").error)
            assertEquals("refused", api.set(Seed.T4, "child_lock", "\"on\"").error)
            // number: within min/max, refused (never clamped) outside
            assertEquals(
                600,
                api
                    .set(Seed.T4, "cleaning_delay", "600")["entity"]
                    .jsonObject["value"]!!
                    .jsonPrimitive.int,
            )
            assertEquals(
                "Cleaning Delay must be between 0 and 3600",
                api
                    .set(Seed.T4, "cleaning_delay", "-1")
                    .json["message"]!!
                    .jsonPrimitive.content,
            )
            assertEquals("refused", api.set(Seed.T4, "cleaning_delay", "true").error)
            // select: one of its labels; the stored value is the device's, the display the label
            val select = api.set(Seed.T4, "sand_type", "\"tofu\"")["entity"].jsonObject
            assertEquals(2, select["value"]!!.jsonPrimitive.int)
            assertEquals("tofu", select["display"]!!.jsonPrimitive.content)
            assertEquals(listOf("bentonite", "tofu", "mixed"), select["options"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("refused", api.set(Seed.T4, "sand_type", "\"sand\"").error)
            // time: HH:MM or HH:MM:SS, stored as seconds since midnight
            assertEquals(
                30600,
                api
                    .set(Seed.W7H, "flush_time", "\"08:30\"")["entity"]
                    .jsonObject["value"]!!
                    .jsonPrimitive.int,
            )
            assertEquals("refused", api.set(Seed.W7H, "flush_time", "\"24:00\"").error)
            // text: at most 255 characters, schedule texts checked like the panel's editors
            assertEquals(200, api.set(Seed.W7H, "refill_disturb_period", "\"22:00-06:00,12:00-13:00\"").status)
            assertEquals("refused", api.set(Seed.W7H, "refill_disturb_period", "\"tonight\"").error)
            assertEquals("refused", api.set(Seed.W7H, "voice_disturb_period", "\"${"x".repeat(256)}\"").error)
            assertEquals(
                200,
                api
                    .set(
                        Seed.T4,
                        "cleaning_schedule",
                        "\"[{\\\"id\\\":1,\\\"repeats\\\":\\\"1,7\\\",\\\"time\\\":120,\\\"type\\\":0}]\"",
                    ).status,
            )
            assertEquals("refused", api.set(Seed.T4, "cleaning_schedule", "\"[{\\\"time\\\":5000}]\"").error)
            // read-only
            assertEquals("not_settable", api.set(Seed.T4, "litter_weight", "1").error)
        }

    @Test
    fun `local entities answer delivered local, MQTT devices mqtt, offline ones queued`() =
        mockTest { _, api ->
            api.login()
            assertEquals("local", api.set(Seed.D4SH, "hopper1_portions", "3")["delivered"].jsonPrimitive.content)
            assertEquals("mqtt", api.set(Seed.T5, "child_lock", "true")["delivered"].jsonPrimitive.content)
            api.client.control(io.ktor.http.HttpMethod.Post, "/devices/${Seed.T5}/offline")
            assertEquals("queued", api.set(Seed.T5, "child_lock", "false")["delivered"].jsonPrimitive.content)
        }

    @Test
    fun `destructive actions need confirm true, the others no body`() =
        mockTest { _, api ->
            api.login()
            val detail = api.get("/devices/{id}", "/devices/${Seed.T6}")["device"].jsonObject
            val actions = detail["actions"]!!.jsonArray.map { it.jsonObject }
            // Destructive last, as the real server orders them.
            val firstDestructive = actions.indexOfFirst { it["destructive"]!!.jsonPrimitive.content.toBoolean() }
            assertTrue(actions.drop(firstDestructive).all { it["destructive"]!!.jsonPrimitive.content.toBoolean() })
            assertEquals("confirmation_required", api.post(action, "/devices/${Seed.T6}/actions/ptz_flip").error)
            assertEquals("confirmation_required", api.post(action, "/devices/${Seed.T6}/actions/ptz_flip", """{"confirm":"yes"}""").error)
            assertEquals(200, api.post(action, "/devices/${Seed.T6}/actions/ptz_flip", """{"confirm":true}""").status)
            assertEquals(200, api.post(action, "/devices/${Seed.T6}/actions/ptz_left").status)
            assertEquals("action_not_found", api.post(action, "/devices/${Seed.K3}/actions/reset_n60").error)
        }

    @Test
    fun `an accessory is written through its parent on MQTT, or refused with 409, never queued`() =
        mockTest { _, api ->
            api.login()
            // The W5's parent (the YumShare Dual-Hopper) holds an MQTT session.
            assertEquals("confirmation_required", api.post(action, "/devices/${Seed.W5}/actions/w5_reset_filter").error)
            val reset = api.post(action, "/devices/${Seed.W5}/actions/w5_reset_filter", """{"confirm":true}""")
            assertEquals("mqtt", reset["delivered"].jsonPrimitive.content)
            assertEquals("mqtt", api.set(Seed.W5, "w5_light", "false")["delivered"].jsonPrimitive.content)
            // The CTW3's parent (the Fresh Element Solo) polls HTTP: nothing is sent or queued.
            val refused = api.post(action, "/devices/${Seed.CTW3}/actions/ctw3_reset_filter", """{"confirm":true}""")
            assertEquals(409, refused.status)
            assertEquals("accessory_unreachable", refused.error)
            assertEquals("accessory_unreachable", api.set(Seed.CTW3, "ctw3_light", "false").error)
            // The value is checked before the relay is looked for.
            assertEquals("refused", api.set(Seed.CTW3, "ctw3_light", "1").error)
            // A parent that goes offline takes its accessory's relay with it.
            api.client.control(io.ktor.http.HttpMethod.Post, "/devices/${Seed.D4SH}/offline")
            assertEquals("accessory_unreachable", api.set(Seed.W5, "w5_light", "true").error)
            // An accessory has no network of its own to drop.
            assertEquals(400, api.client.control(io.ktor.http.HttpMethod.Post, "/devices/${Seed.W5}/offline").status)
        }

    @Test
    fun `a CTW3 shows its battery mode, refuses a mode on it, and pauses, with PetKit's battery ranges`() =
        mockTest { _, api ->
            api.login()

            suspend fun ctw3Entity(key: String) =
                api
                    .get("/devices/{id}", "/devices/${Seed.CTW3}")["device"]
                    .arr("entities")
                    .first { it.str("key") == key }
                    .jsonObject
            // Plugged in, in smart mode; then relayed over MQTT once its parent holds a session.
            assertEquals("smart", ctw3Entity("ctw3_mode")["display"]!!.jsonPrimitive.content)
            assertEquals(false, ctw3Entity("ctw3_battery_mode")["value"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(200, api.client.control(HttpMethod.Put, "/devices/${Seed.D4}/transport", """{"transport":"mqtt"}""").status)
            assertEquals("mqtt", api.set(Seed.CTW3, "ctw3_mode", "\"normal\"")["delivered"].jsonPrimitive.content)

            // Unplugged: mode 3 is shown as `battery`, never offered, and a mode write is refused.
            api.client.control(HttpMethod.Put, "/devices/${Seed.CTW3}/state", """{"states.mode":3,"states.batteryMode":1}""")
            val mode = ctw3Entity("ctw3_mode")
            assertEquals(3, mode["value"]!!.jsonPrimitive.int)
            assertEquals("battery", mode["display"]!!.jsonPrimitive.content)
            assertEquals(listOf("normal", "smart"), mode["options"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(true, ctw3Entity("ctw3_battery_mode")["value"]!!.jsonPrimitive.content.toBoolean())
            val refused = api.set(Seed.CTW3, "ctw3_mode", "\"smart\"")
            assertEquals(422, refused.status)
            assertEquals("on_battery", refused.error)
            assertTrue("battery" in refused.json["message"]!!.jsonPrimitive.content)
            assertEquals("refused", api.set(Seed.CTW3, "ctw3_mode", "\"battery\"").error)
            assertEquals(3, ctw3Entity("ctw3_mode")["value"]!!.jsonPrimitive.int)

            // Pause is allowed on battery: `ctw3_working` false is paused, true runs again.
            val paused = api.set(Seed.CTW3, "ctw3_working", "false")["entity"].jsonObject
            assertEquals(false, paused["value"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(
                true,
                api
                    .set(Seed.CTW3, "ctw3_working", "true")["entity"]
                    .jsonObject["value"]!!
                    .jsonPrimitive.content
                    .toBoolean(),
            )

            // The battery times, in seconds, within PetKit's own ranges, in any mode.
            val work = ctw3Entity("ctw3_energy_interval")
            assertEquals(listOf(15, 300, 15), listOf("min", "max", "step").map { work[it]!!.jsonPrimitive.int })
            val sleep = ctw3Entity("ctw3_sleep_time")
            assertEquals(listOf(60, 10800, 60), listOf("min", "max", "step").map { sleep[it]!!.jsonPrimitive.int })
            assertEquals(
                120,
                api
                    .set(Seed.CTW3, "ctw3_energy_interval", "120")["entity"]
                    .jsonObject["value"]!!
                    .jsonPrimitive.int,
            )
            assertEquals("refused", api.set(Seed.CTW3, "ctw3_energy_interval", "301").error)
            assertEquals("refused", api.set(Seed.CTW3, "ctw3_sleep_time", "30").error)

            // Plugged in again, its real mode shows, and a mode can be picked again, un-pausing it.
            api.client.control(HttpMethod.Put, "/devices/${Seed.CTW3}/state", """{"states.mode":1,"states.batteryMode":0}""")
            api.set(Seed.CTW3, "ctw3_working", "false")
            assertEquals(
                "smart",
                api
                    .set(Seed.CTW3, "ctw3_mode", "\"smart\"")["entity"]
                    .jsonObject["display"]!!
                    .jsonPrimitive.content,
            )
            assertEquals(true, ctw3Entity("ctw3_working")["value"]!!.jsonPrimitive.content.toBoolean())
        }

    @Test
    fun `T4 calibration is refused while busy or with a cat inside`() =
        mockTest(testConfig().copy(busySec = 30.0)) { _, api ->
            api.login()
            api.post(action, "/devices/${Seed.T4}/actions/cleaning_start")
            val r = api.post(action, "/devices/${Seed.T4}/actions/finish_litter_calibration")
            assertEquals(422, r.status)
            assertEquals("refused", r.error)
        }

    @Test
    fun `the feeding plan - replace, pause, read back, validate`() =
        mockTest { _, api ->
            api.login()
            val schedule = "/devices/{id}/schedule"
            val single = api.get(schedule, "/devices/${Seed.D4}/schedule")["schedule"].jsonObject
            assertEquals(false, single["dual_hopper"]!!.jsonPrimitive.content.toBoolean())
            val meal =
                single["groups"]!!
                    .jsonArray[0]
                    .jsonObject["meals"]!!
                    .jsonArray[0]
                    .jsonObject
            assertEquals(JsonNull, meal["amount1"])
            assertTrue(meal["amount"]!!.jsonPrimitive.int > 0)

            val r =
                api.put(
                    schedule,
                    "/devices/${Seed.D4SH}/schedule",
                    """{"active":false,"groups":[{"days":[1,7],"meals":[{"time":30600,"amount1":2},""" +
                        """{"id":"keep","time":64800,"amount1":1,"amount2":3}]}]}""",
                )
            assertEquals("mqtt", r["delivered"].jsonPrimitive.content)
            val plan = r["schedule"].jsonObject
            assertEquals(false, plan["active"]!!.jsonPrimitive.content.toBoolean())
            val meals =
                plan["groups"]!!
                    .jsonArray[0]
                    .jsonObject["meals"]!!
                    .jsonArray
                    .map { it.jsonObject }
            assertEquals("n_30600", meals[0]["id"]!!.jsonPrimitive.content)
            assertEquals(0, meals[0]["amount2"]!!.jsonPrimitive.int)
            assertEquals("keep", meals[1]["id"]!!.jsonPrimitive.content)
            val pause =
                api
                    .get("/devices/{id}", "/devices/${Seed.D4SH}")["device"]
                    .jsonObject["entities"]!!
                    .jsonArray
                    .map { it.jsonObject }
                    .first { it["key"]!!.jsonPrimitive.content == "feeding_schedule_active" }
            assertEquals(false, pause["value"]!!.jsonPrimitive.content.toBoolean())

            fun bad(body: String) = body
            for (body in listOf(
                bad("""{"active":"no"}"""),
                bad("""{"groups":{}}"""),
                bad("""{"groups":[{"days":[],"meals":[]}]}"""),
                bad("""{"groups":[{"days":[1],"meals":[{"time":"08:00","amount1":1}]}]}"""),
                bad("""{"groups":[{"days":[1],"meals":[{"time":100}]}]}"""),
                bad("""{"groups":[{"days":[1],"meals":[{"time":100,"amount1":256}]}]}"""),
                bad("""{"groups":[{"days":[1],"meals":[{"time":86400,"amount1":1}]}]}"""),
            )) {
                assertEquals("invalid_schedule", api.put(schedule, "/devices/${Seed.D4SH}/schedule", body).error, body)
            }
            assertEquals("bad_request", api.put(schedule, "/devices/${Seed.D4SH}/schedule", "{}").error)
            // A single hopper needs `amount`.
            assertEquals(
                "invalid_schedule",
                api.put(schedule, "/devices/${Seed.D4}/schedule", """{"groups":[{"days":[1],"meals":[{"time":100,"amount1":1}]}]}""").error,
            )
        }

    @Test
    fun `one-off feeds - add, list soonest first, cancel, validate`() =
        mockTest { world, api ->
            api.login()
            val feeds = "/devices/{id}/deferred-feeds"
            val day = world.clock.today().plusDays(2)
            val later = api.post(feeds, "/devices/${Seed.D4SH}/deferred-feeds", """{"date":"$day","time":"18:30","amount2":2}""")
            val sooner =
                api.post(
                    feeds,
                    "/devices/${Seed.D4SH}/deferred-feeds",
                    """{"date":"$day","time":"7:05","amount1":1,"amount2":1}""",
                )
            val feed = sooner["feed"].jsonObject
            assertEquals("d_${day.toString().replace("-", "")}_25500", feed["id"]!!.jsonPrimitive.content)
            assertEquals(JsonNull, feed["amount"])
            assertEquals(0, later["feed"].jsonObject["amount1"]!!.jsonPrimitive.int)
            val listed = api.get(feeds, "/devices/${Seed.D4SH}/deferred-feeds")["deferred_feeds"].jsonArray.map { (it as JsonObject)["id"] }
            assertEquals(listOf(sooner["feed"].jsonObject["id"], later["feed"].jsonObject["id"]), listed)

            for (body in listOf(
                """{"time":"08:00","amount1":1}""",
                """{"date":"$day","time":"08:00"}""",
                """{"date":"$day","time":"08:00","amount1":1.5}""",
                """{"date":"$day","time":"08:00","amount1":300}""",
                """{"date":"$day","time":"25:00","amount1":1}""",
                """{"date":"2020-02-30","time":"08:00","amount1":1}""",
                """{"date":"${world.clock.today().minusDays(1)}","time":"08:00","amount1":1}""",
            )) {
                assertEquals("invalid_feed", api.post(feeds, "/devices/${Seed.D4SH}/deferred-feeds", body).error, body)
            }
            assertEquals(
                "invalid_feed",
                api.post(feeds, "/devices/${Seed.D4}/deferred-feeds", """{"date":"$day","time":"08:00","amount1":1}""").error,
            )
            val id = later["feed"].jsonObject["id"]!!.jsonPrimitive.content
            assertEquals(
                "mqtt",
                api
                    .delete(
                        "/devices/{id}/deferred-feeds/{feed_id}",
                        "/devices/${Seed.D4SH}/deferred-feeds/$id",
                    )["delivered"]
                    .jsonPrimitive.content,
            )
            assertEquals(
                "feed_not_found",
                api.delete("/devices/{id}/deferred-feeds/{feed_id}", "/devices/${Seed.D4SH}/deferred-feeds/$id").error,
            )
        }

    @Test
    fun `a due one-off feed is served and leaves the list`() =
        mockTest { world, api ->
            api.login()
            val scenario =
                io.github.gamso.petkitlocal.mock.scenario
                    .Scenario(world, 0.0)
            val day = world.clock.today().plusDays(1)
            api.post("/devices/{id}/deferred-feeds", "/devices/${Seed.D4}/deferred-feeds", """{"date":"$day","time":"12:00","amount":3}""")
            val before = world.timeline.cards.size
            world.clock.advance(2 * 86400.0 - 60)
            synchronized(world.lock) { scenario.serveDue() } // first call only arms the window after the jump
            world.clock.advance(1.0)
            synchronized(world.lock) { scenario.serveDue() }
            scenario.close()
            assertTrue(
                api.get("/devices/{id}/deferred-feeds", "/devices/${Seed.D4}/deferred-feeds")["deferred_feeds"].jsonArray.none {
                    it.jsonObject["id"]!!
                        .jsonPrimitive.content
                        .startsWith("d_${day.toString().replace("-", "")}")
                },
            )
            kotlinx.coroutines.delay(300)
            assertTrue(world.timeline.cards.size > before)
        }
}
