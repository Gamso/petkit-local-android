package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.ApiException
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Episodes played live: each step changes the device's state (a `state` message), and each
 * report adds or changes a card (a `timeline` message). Timings come from the config, so a test
 * running with 0 s sees the whole sequence at once and a demo sees it unfold.
 */

private const val IDLE = -1

/** Whether the device still exists as this object (a reset or a removal drops it). */
private fun World.alive(device: MockDevice) = devices[device.id] === device

private fun MockDevice.incr(
    path: String,
    by: Double,
) {
    if (path in doc || model.entities.any { it.valuePath == path }) this[path] = (number(path) ?: 0.0) + by
}

private fun MockDevice.setIfShown(
    path: String,
    value: Any?,
) {
    if (model.entities.any { it.valuePath == path }) this[path] = value
}

/**
 * A pet uses a litter box: in-progress card, then the summary replacing it, then (camera models)
 * the recording, then (auto clean on) the cleaning cycle attached to the same card.
 *
 * @return the in-progress card's id.
 */
fun World.playVisit(
    device: MockDevice,
    petId: Int?,
    weightG: Int? = null,
    durationSec: Int? = null,
    autoClean: Boolean? = null,
    healthAlert: Boolean = false,
): Int {
    val pet = petId?.let { pets[it] }
    val started =
        change(device) {
            device["state.petInTime"] = 1
            visitStarted(device, clock.now(), pet?.id)
        }
    publish(started)
    later(config.visitSec) {
        if (!alive(device)) return@later
        val duration = durationSec ?: random.nextInt(35, 190)
        val weight = weightG ?: pet?.let { (it.weightG * (1 + random.nextDouble(-0.02, 0.02))).roundToInt() } ?: random.nextInt(3500, 6500)
        val now = clock.now()
        val finished =
            change(device) {
                device["state.petInTime"] = 0
                device.setIfShown("state.petWeight", weight)
                device.setIfShown("state.lastPet", pet?.name)
                device.setIfShown("state.lastVisit", iso(now))
                device.incr("state.usedTimes", 1.0)
                device.incr("state.totalTime", duration.toDouble())
                visitFinished(device, started, now, duration, weight, random.nextInt(15, 60), healthAlert, device.model.isCamera)
            }
        publish(finished, replaces = listOf(started.id))
        if (device.model.isCamera) {
            later(config.videoDelaySec) {
                if (!alive(device) || timeline.cards[finished.id] !== finished) return@later
                videoArrived(device, finished)
                publish(finished)
            }
        }
        val auto = autoClean ?: (device["settings.autoWork"]?.let(MockDevice::truthy) ?: false)
        if (auto) later(config.busySec) { if (alive(device)) runLitterCycle(device, "5", Reason.AUTO, attachTo = finished) }
    }
    return started.id
}

/**
 * A litter box's mechanism runs: `device_status` moves to the work mode, back to idle after
 * `busySec`, and the completion lands in the history — on [attachTo]'s card for a cleaning after
 * a visit, otherwise as a card of its own that replaces the in-progress one.
 */
fun World.runLitterCycle(
    device: MockDevice,
    eventType: String,
    reason: Reason,
    attachTo: Card? = null,
) {
    val (workState, what) =
        when (eventType) {
            "5" -> 0 to "cleaning"
            "6" -> 1 to "litter empty"
            "8" -> 2 to "deodorizing"
            "7" -> 3 to "reset"
            "12" -> 4 to "levelling"
            else -> error("no cycle for event $eventType")
        }
    // A box already running a cycle ignores another start, as the firmware does.
    if (device.isBusy) return
    val start = clock.now()
    val startRow =
        change(device) {
            device["state.workingState"] = workState
            cleaningStart(device, start, reason)
        }
    if (attachTo == null) publish(card(startRow, "event"))
    device.busyJob?.cancel()
    device.busyJob =
        later(config.busySec) {
            if (!alive(device)) return@later
            finishCycle(device, eventType, reason, what, startRow, attachTo)
        }
}

private fun World.finishCycle(
    device: MockDevice,
    eventType: String,
    reason: Reason,
    what: String,
    startRow: EventRow,
    attachTo: Card?,
) {
    val done =
        change(device) {
            device["state.workingState"] = IDLE
            when (eventType) {
                "5" -> {
                    device.setIfShown("state.lastClean", iso(clock.now()))
                    device.number("state.sandPercent")?.let { device["state.sandPercent"] = max(0.0, it - 1) }
                    device.number("state.sandWeight")?.let { device["state.sandWeight"] = max(0.0, it - 60) }
                }

                "6" -> {
                    device.setIfShown("state.sandPercent", 0)
                    device.setIfShown("state.sandWeight", 0)
                    device.setIfShown("state.lastClean", iso(clock.now()))
                }
            }
            cleaningDone(device, clock.now(), reason, eventType, what)
        }
    if (attachTo != null && timeline.cards[attachTo.id] === attachTo) {
        attachTo.steps += listOf(startRow.id, done.id)
        publish(attachTo)
    } else {
        publish(cleaningCard(startRow, done), replaces = listOf(startRow.id))
    }
}

/** A feeder serves [portions] (or [a1]/[a2] on a Dual-Hopper); one still dispensing ignores it. */
fun World.playFeed(
    device: MockDevice,
    source: FeedSource,
    portions: Int?,
    a1: Int? = null,
    a2: Int? = null,
) {
    if (device.isBusy) return
    val total = if (device.isDualHopper) (a1 ?: 0) + (a2 ?: 0) else portions ?: 1
    change(device) { device.setIfShown("state.feeding", 1) }
    device.busyJob =
        later(config.busySec) {
            if (!alive(device)) return@later
            val card =
                change(device) {
                    device.setIfShown("state.feeding", 0)
                    device.incr("state.feedState.times", 1.0)
                    device.incr("state.feedState.realAmountTotal", total * GRAMS_PER_PORTION)
                    device.setIfShown("state.lastFeed", iso(clock.now()))
                    if (device.isDualHopper) countHoppers(device, source, a1 ?: 0, a2 ?: 0)
                    device.number("state.bowl")?.let { device["state.bowl"] = minOf(100.0, it + 15) }
                    feedCard(device, clock.now(), source, portions ?: 1, a1, a2)
                }
            publish(card)
        }
}

private fun countHoppers(
    device: MockDevice,
    source: FeedSource,
    a1: Int,
    a2: Int,
) {
    val manual = source == FeedSource.APP
    device.incr(if (manual) "state.feedState.addAmountTotal1" else "state.feedState.planAmountTotal1", a1.toDouble())
    device.incr(if (manual) "state.feedState.addAmountTotal2" else "state.feedState.planAmountTotal2", a2.toDouble())
    if (!manual) {
        device.incr("state.feedState.planRealAmountTotal1", a1.toDouble())
        device.incr("state.feedState.planRealAmountTotal2", a2.toDouble())
    }
}

/** A pet drinks from a fountain (a W7H recognises it; the others do not report drinks). */
fun World.playDrink(
    device: MockDevice,
    petId: Int?,
    durationSec: Int? = null,
): Card {
    val duration = durationSec ?: random.nextInt(8, 60)
    val card =
        change(device) {
            device.setIfShown("state.lastDrink", iso(clock.now()))
            device.setIfShown("state.lastPetDetect", iso(clock.now()))
            drinkCard(device, clock.now(), duration, petId)
        }
    publish(card)
    return card
}

fun World.raiseError(
    device: MockDevice,
    message: String,
): Card {
    val card =
        change(device) {
            device.setIfShown("state.errorMsg", message)
            if (device.isLitter && message.contains("full")) device.setIfShown("state.boxFull", 1)
            errorCard(device, clock.now(), message)
        }
    publish(card)
    return card
}

fun World.clearError(device: MockDevice): Card {
    val card =
        change(device) {
            device.setIfShown("state.errorMsg", null)
            device.setIfShown("state.boxFull", 0)
            errorClearedCard(device, clock.now())
        }
    publish(card)
    return card
}

/** A cycle that only moves state: on for `busySec`, then back. */
private fun World.pulse(
    device: MockDevice,
    path: String,
    on: Any,
    off: Any,
    after: () -> Unit = {},
) {
    change(device) { device[path] = on }
    device.busyJob?.cancel()
    device.busyJob =
        later(config.busySec) {
            if (!alive(device)) return@later
            change(device) {
                device[path] = off
                after()
            }
        }
}

/** Buttons that run a litter box's mechanism, by the event code their completion reports. */
private val LITTER_CYCLES = mapOf("cleaning_start" to "5", "dump_litter" to "6", "deodorize" to "8", "reset" to "7", "level_litter" to "12")

/** Buttons that only move one state field for `busySec`: `(path, on, off)`. */
private val PULSES =
    mapOf(
        "open_sealed_door" to Triple("state.sealDoorState", 1, 0),
        "fountain_flush" to Triple("state.flushState", 1, 0),
        "fountain_refill" to Triple("state.addWaterState", 1, 0),
        "fountain_reset_circulation_pump" to Triple("state.pumpState", 0, 1),
        "fountain_drain" to Triple("state.waterPumpState", 1, 0),
    )

/** Consumable resets: the field and the value a fresh consumable reads. */
private val RESETS =
    mapOf(
        "reset_n50" to ("state.deodorantLeftDays" to 30),
        "reset_n60" to ("state.sprayLeftDays" to 30),
        "reset_bagbox" to ("state.packageCount" to 0),
        "reset_desiccant" to ("state.desiccantLeftDays" to 30),
        "w5_reset_filter" to ("consumables.filterPercentage" to 100),
        "ctw3_reset_filter" to ("consumables.filterPercent" to 100),
    )

/**
 * What a button does to the fake device. Called after the route checked the action exists for
 * this model and that a destructive one was confirmed. Buttons with nothing a sensor of ours
 * shows (light, power_on, power_off, play_sound) are delivered and change nothing.
 */
fun World.runAction(
    device: MockDevice,
    key: String,
) {
    LITTER_CYCLES[key]?.let { return runLitterCycle(device, it, Reason.MANUAL) }
    PULSES[key]?.let { (path, on, off) -> return pulse(device, path, on, off) }
    RESETS[key]?.let { (path, value) -> return change(device) { device.setIfShown(path, value) } }
    when (key) {
        "maintenance_start", "maintenance_stop", "pause", "resume", "finish_litter_calibration" -> litterControl(device, key)
        "pack_waste" -> pulse(device, "state.packState", 1, 0) { device.incr("state.packageCount", 1.0) }
        "ptz_left", "ptz_right", "ptz_flip" -> moveCamera(device, key)
        "feed", "feed_hopper_1", "feed_hopper_2", "cancel_manual_feed", "food_replenished" -> feederAction(device, key)
        "fountain_deep_clean" -> deepClean(device)
    }
}

private fun World.litterControl(
    device: MockDevice,
    key: String,
) {
    val workState = device.number("state.workingState")?.toInt()
    when (key) {
        "maintenance_start" -> {
            device.busyJob?.cancel()
            change(device) { device["state.workingState"] = MAINTENANCE }
        }

        "maintenance_stop" -> {
            change(device) { device["state.workingState"] = IDLE }
        }

        // The device stops where it is; resume finishes the cycle.
        "pause" -> {
            if (device.isBusy) device.busyJob?.cancel()
        }

        "resume" -> {
            val paused = workState != null && workState != IDLE && workState != MAINTENANCE
            if (paused && !device.isBusy) {
                device.busyJob = later(config.busySec) { if (alive(device)) change(device) { device["state.workingState"] = IDLE } }
            }
        }

        "finish_litter_calibration" -> {
            val idle = workState == IDLE && !device.isBusy
            val catInside = device["state.petInTime"]?.let(MockDevice::truthy) == true
            if (!device.online || !idle || catInside) {
                throw ApiException(422, "refused", "Wait until the device is online, idle and no cat is inside")
            }
            pulse(device, "state.workingState", LITTER_CALIBRATION, IDLE)
        }
    }
}

private fun World.moveCamera(
    device: MockDevice,
    key: String,
) {
    val position = device.number("state.cameraPosition")?.toInt() ?: 0
    val next =
        when (key) {
            "ptz_left" -> position - 1
            "ptz_right" -> position + 1
            else -> if (position == 0) 180 else 0
        }
    change(device) { device["state.cameraPosition"] = next }
}

private fun World.feederAction(
    device: MockDevice,
    key: String,
) {
    when (key) {
        "feed" -> {
            if (device.isDualHopper) {
                playFeed(device, FeedSource.APP, null, portionsOf(device, 1), portionsOf(device, 2))
            } else {
                playFeed(device, FeedSource.APP, 1)
            }
        }

        "feed_hopper_1" -> {
            playFeed(device, FeedSource.APP, null, portionsOf(device, 1), 0)
        }

        "feed_hopper_2" -> {
            playFeed(device, FeedSource.APP, null, 0, portionsOf(device, 2))
        }

        "cancel_manual_feed" -> {
            if (device.isBusy) {
                device.busyJob?.cancel()
                change(device) { device.setIfShown("state.feeding", 0) }
            }
        }

        "food_replenished" -> {
            change(device) {
                listOf("state.food", "state.food1", "state.food2").forEach { device.setIfShown(it, 2) }
                device.setIfShown("state.foodLow", 0)
            }
        }
    }
}

private fun World.deepClean(device: MockDevice) {
    val start = clock.now()
    pulse(device, "state.disinfectState", 3, 0) {
        device["state.lastDeepClean"] = iso(clock.now())
        device["state.lastDeepCleanResult"] = 0
        val card = deepCleanCard(device, start, clock.now(), Reason.MANUAL)
        later(0.0) { if (timeline.cards[card.id] === card) publish(card) }
    }
}

private const val MAINTENANCE = 9
private const val LITTER_CALIBRATION = 6

private fun portionsOf(
    device: MockDevice,
    hopper: Int,
): Int = device.number("local.feedAmount$hopper")?.toInt() ?: 1

const val GRAMS_PER_PORTION = 10.0
