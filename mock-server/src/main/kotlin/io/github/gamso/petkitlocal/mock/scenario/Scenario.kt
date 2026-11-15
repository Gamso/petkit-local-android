package io.github.gamso.petkitlocal.mock.scenario

import io.github.gamso.petkitlocal.mock.seed.Seed
import io.github.gamso.petkitlocal.mock.state.FeedSource
import io.github.gamso.petkitlocal.mock.state.MockDevice
import io.github.gamso.petkitlocal.mock.state.World
import io.github.gamso.petkitlocal.mock.state.clearError
import io.github.gamso.petkitlocal.mock.state.petAppearedCard
import io.github.gamso.petkitlocal.mock.state.playDrink
import io.github.gamso.petkitlocal.mock.state.playFeed
import io.github.gamso.petkitlocal.mock.state.playVisit
import io.github.gamso.petkitlocal.mock.state.raiseError
import io.github.gamso.petkitlocal.mock.state.syncPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Plays a household's day on a timer: every second it serves the meals and one-off feeds that
 * fell due; every [intervalSec] it rolls one event — a visit, a drink, a consumable running down,
 * a device dropping off and coming back, an error and its clearing, a cat passing a camera.
 * Each one changes state and history exactly as the control API's triggers do, so a socket sees
 * the same messages either way.
 */
class Scenario(
    private val world: World,
    intervalSec: Double,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = LoggerFactory.getLogger(Scenario::class.java)
    private var loop: Job? = null
    private var lastDue = world.clock.now()

    @Volatile var intervalSec: Double = intervalSec
        private set

    val running: Boolean get() = loop?.isActive == true

    fun start() {
        if (intervalSec <= 0 || running) return
        lastDue = world.clock.now()
        loop =
            scope.launch {
                var sinceStep = 0.0
                while (isActive) {
                    delay(TICK_MS)
                    runCatching {
                        synchronized(world.lock) { serveDue() }
                        sinceStep += TICK_MS / 1000.0
                        if (sinceStep >= this@Scenario.intervalSec) {
                            sinceStep = 0.0
                            synchronized(world.lock) { step() }
                        }
                    }.onFailure { log.warn("scenario step failed", it) }
                }
            }
        log.info("scenario engine on, one event every {} s", intervalSec)
    }

    fun stop() {
        loop?.cancel()
        loop = null
    }

    fun configure(interval: Double) {
        stop()
        intervalSec = interval
        start()
    }

    fun close() = scope.cancel()

    /** Rolls one event now, whatever the timer says; returns what happened. */
    fun tick(): String = synchronized(world.lock) { step() }

    // --- due meals --------------------------------------------------------------------------------

    /** Serves every planned meal and one-off feed whose time fell in `(lastDue, now]`. */
    fun serveDue() {
        val now = world.clock.now()
        // After a clock jump, do not replay a month of meals.
        if (now < lastDue || now - lastDue > 86400) lastDue = now
        val from = lastDue
        lastDue = now
        world.devices.values.filter { it.isFeeder }.forEach { device ->
            val plan = device.plan ?: return@forEach
            plan.deferred.filter { it.fireAt <= now }.forEach { feed ->
                world.change(device, reported = false) {
                    plan.deferred.remove(feed)
                    device.syncPlan()
                }
                world.playFeed(device, FeedSource.ONE_OFF, feed.a, feed.a1, feed.a2)
            }
            if (plan.active) serveMeals(device, from, now)
        }
    }

    private fun serveMeals(
        device: MockDevice,
        from: Double,
        now: Double,
    ) {
        val plan = device.plan ?: return
        val days = listOf(world.clock.localDateTime(from).toLocalDate(), world.clock.localDateTime(now).toLocalDate()).distinct()
        for (date in days) {
            val dayNumber = Seed.deviceDay(date.dayOfWeek)
            val midnight = date.atStartOfDay(world.clock.zone).toEpochSecond().toDouble()
            plan.groups
                .filter { dayNumber in it.days }
                .flatMap { it.meals }
                .filter { midnight + it.t > from && midnight + it.t <= now }
                .forEach { meal ->
                    log.info("meal {} due on device {} at {}", meal.id, device.id, Instant.ofEpochSecond((midnight + meal.t).toLong()))
                    world.playFeed(device, FeedSource.SCHEDULE, meal.a, meal.a1, meal.a2)
                }
        }
    }

    // --- random events ----------------------------------------------------------------------------

    private val rng get() = world.random

    private fun step(): String {
        val roll = rng.nextDouble()
        return when {
            roll < 0.40 -> visit()
            roll < 0.55 -> drink()
            roll < 0.70 -> consumable()
            roll < 0.78 -> offlineBlip()
            roll < 0.83 -> error()
            roll < 0.90 -> appeared()
            else -> signal()
        }
    }

    private fun online(predicate: (MockDevice) -> Boolean) = world.devices.values.filter { it.online && predicate(it) }

    private fun visit(): String {
        val box =
            online { it.isLitter && !it.isBusy && it["state.petInTime"]?.let(MockDevice::truthy) != true }.randomOrNull(rng)
                ?: return "no idle litter box"
        val pets = world.pets.values.filter { box.id in it.deviceIds }
        val pet = if (rng.nextDouble() < 0.9) pets.randomOrNull(rng) else null
        world.playVisit(box, pet?.id)
        return "visit on ${box.id}"
    }

    private fun drink(): String {
        val fountain = online { it.type == "w7h" }.randomOrNull(rng) ?: return "no fountain online"
        world.playDrink(fountain, world.pets.keys.randomOrNull(rng))
        return "drink on ${fountain.id}"
    }

    private fun consumable(): String {
        val device = world.devices.values.randomOrNull(rng) ?: return "no device"
        val path =
            CONSUMABLES.filter { p -> device.model.entities.any { it.valuePath == p } }.randomOrNull(rng)
                ?: return "nothing to wear on ${device.id}"
        world.change(device) {
            device[path] = maxOf(0.0, (device.number(path) ?: 0.0) - 1)
        }
        return "$path down on ${device.id}"
    }

    private fun offlineBlip(): String {
        // An accessory goes offline with its parent, never on its own.
        if (world.devices.values.any { !it.online && !it.isAccessory }) return "a device is already offline"
        val device = online { !it.isAccessory }.randomOrNull(rng) ?: return "no device"
        world.setOnline(device, false)
        world.later(intervalSec * 2) { if (world.devices[device.id] === device) world.setOnline(device, true) }
        return "${device.id} offline"
    }

    private fun error(): String {
        val box = online { it.isLitter && (it["state.errorMsg"] ?: JsonNull) == JsonNull }.randomOrNull(rng) ?: return "no box"
        world.raiseError(box, "bin full")
        world.later(intervalSec * 2) { if (world.devices[box.id] === box) world.clearError(box) }
        return "error on ${box.id}"
    }

    private fun appeared(): String {
        val box = online { it.isLitter && it.model.isCamera }.randomOrNull(rng) ?: return "no camera box"
        val card = world.petAppearedCard(box, world.clock.now(), world.pets.keys.randomOrNull(rng))
        world.publish(card)
        return "pet seen by ${box.id}"
    }

    private fun signal(): String {
        val device = online { it.has("rssi") }.randomOrNull(rng) ?: return "no device"
        world.change(device) { device["state.rssi"] = -40 - rng.nextInt(0, 35) }
        return "rssi on ${device.id}"
    }

    companion object {
        const val TICK_MS = 1000L
        val CONSUMABLES =
            listOf(
                "state.deodorantLeftDays",
                "state.sprayLeftDays",
                "state.desiccantLeftDays",
                "state.sandPercent",
                "consumables.filterPercentage",
                "consumables.filterPercent",
                "states.batteryPercent",
                "consumables.liquid",
                "consumables.battery",
            )
    }
}
