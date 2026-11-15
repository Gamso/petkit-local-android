package io.github.gamso.petkitlocal.mock.seed

import io.github.gamso.petkitlocal.mock.state.Card
import io.github.gamso.petkitlocal.mock.state.DeferredFeed
import io.github.gamso.petkitlocal.mock.state.FeedPlan
import io.github.gamso.petkitlocal.mock.state.FeedSource
import io.github.gamso.petkitlocal.mock.state.GRAMS_PER_PORTION
import io.github.gamso.petkitlocal.mock.state.Meal
import io.github.gamso.petkitlocal.mock.state.MealGroup
import io.github.gamso.petkitlocal.mock.state.MockDevice
import io.github.gamso.petkitlocal.mock.state.Pet
import io.github.gamso.petkitlocal.mock.state.Reason
import io.github.gamso.petkitlocal.mock.state.World
import io.github.gamso.petkitlocal.mock.state.cleaningCard
import io.github.gamso.petkitlocal.mock.state.cleaningRows
import io.github.gamso.petkitlocal.mock.state.deepCleanCard
import io.github.gamso.petkitlocal.mock.state.drinkCard
import io.github.gamso.petkitlocal.mock.state.errorCard
import io.github.gamso.petkitlocal.mock.state.errorClearedCard
import io.github.gamso.petkitlocal.mock.state.feedCard
import io.github.gamso.petkitlocal.mock.state.petAppearedCard
import io.github.gamso.petkitlocal.mock.state.syncPlan
import io.github.gamso.petkitlocal.mock.state.visitFinished
import io.github.gamso.petkitlocal.mock.state.visitStarted
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The fixed household the mock starts from: one device of every family petkit-local supports,
 * two cats, and seven days of history up to "now", drawn from a seeded RNG so a seed always
 * replays the same week (relative to today).
 */
class Seed(
    private val world: World,
    private val rng: Random,
) {
    private val clock = world.clock

    /** Ids are grouped by family so a test can tell them apart at a glance. */
    data class SeedDevice(
        val id: Int,
        val type: String,
        val serial: String?,
        val firmware: String,
    )

    fun build() {
        DEVICES.forEach { s ->
            val device = MockDevice(s.id, world.catalog.model(s.type), s.serial, s.firmware, transportOf(s.type))
            device.lastSeen = clock.now() - rng.nextInt(2, 40)
            world.devices[s.id] = device
            seedValues(device)
            if (device.isFeeder) device.plan = seedPlan(device)
        }
        // The Bluetooth accessories and the devices relaying them: the Pura Max holds the Pura
        // Air in its K3 slot; the W5 is relayed by a feeder on MQTT, so it can be written to; the
        // CTW3's parent polls HTTP, so a write to it is refused with 409.
        ACCESSORIES.forEach { (accessory, parent) -> world.link(world.devices.getValue(accessory), world.devices.getValue(parent)) }
        // A Bluetooth fountain whose last frame decoded three hours ago: out of its parent's range.
        world.devices.getValue(W5).lastSeen = clock.now() - 3 * 3600 - 412
        world.pets[1] = Pet(1, "Mochi", listOf(T3, T4, T5, T6, T7, D4H, W7H), 4200.0, 4310.0, hasPhoto = true)
        world.pets[2] = Pet(2, "Tigrou", listOf(T4, T5, T6, D4SH, W7H), 6000.0, null, hasPhoto = false)
        history()
        world.devices.values.forEach { it.syncPlan() }
    }

    // --- device values ----------------------------------------------------------------------------

    private fun seedValues(device: MockDevice) {
        val values = BASE_VALUES + (PER_TYPE[device.type] ?: emptyMap())
        device.model.entities.forEach { e ->
            if (e.valuePath in device.doc) return@forEach
            val v =
                values[e.valuePath] ?: when (e.component) {
                    "switch" -> if (e.valuePath.substringAfterLast('.') in OFF_BY_DEFAULT) 0 else 1
                    "binary_sensor" -> 0
                    "number" -> e.min ?: 0
                    "select" -> e.optionValues?.first() ?: 0
                    "time" -> 0
                    "text" -> ""
                    else -> return@forEach // a sensor this model has not reported yet: null
                }
            device.doc[e.valuePath] =
                if (v is JsonPrimitive) {
                    v
                } else {
                    io.github.gamso.petkitlocal.mock.state
                        .json(v)
                }
        }
        if (device.isLitter) {
            device.doc["schedule"] = Json.parseToJsonElement("""[{"id":1,"repeats":"1,2,3,4,5,6,7","time":180,"type":0}]""")
        }
        if (device.type == "w7h") {
            device["multi_config.awDisturbMultiRange"] = "22:00-07:00"
            device["multi_config.wlDisturbMultiRange"] = "23:00-06:30"
            device["multi_config.toneMultiRange"] = ""
        }
    }

    private fun seedPlan(device: MockDevice): FeedPlan {
        val dual = device.isDualHopper
        val plan = FeedPlan(dual)

        fun m(
            hh: Int,
            mm: Int,
            a: Int,
            a2: Int = 0,
        ): Meal {
            val t = hh * 3600 + mm * 60
            return if (dual) Meal("n_$t", t, null, a, a2) else Meal("n_$t", t, a, null, null)
        }
        val everyDay = (1..7).toList()
        plan.groups =
            when (device.type) {
                "d4" -> {
                    listOf(MealGroup(everyDay, listOf(m(7, 0, 1), m(12, 0, 1), m(19, 0, 2))))
                }

                "d4s" -> {
                    listOf(MealGroup(everyDay, listOf(m(7, 30, 1, 1), m(18, 30, 1, 0))))
                }

                "d4h" -> {
                    listOf(
                        MealGroup(listOf(2, 3, 4, 5, 6), listOf(m(7, 0, 2), m(18, 0, 2))),
                        MealGroup(listOf(1, 7), listOf(m(8, 30, 2), m(18, 0, 2))),
                    )
                }

                else -> {
                    listOf(MealGroup(everyDay, listOf(m(8, 0, 1, 1), m(13, 0, 0, 1), m(19, 0, 1, 1))))
                }
            }
        // The Gemini's plan is paused (since two days ago, in its history): the app shows that state.
        if (device.type == "d4s") plan.active = false
        if (device.type == "d4h") {
            val day = clock.today().plusDays(1)
            val fireAt =
                day
                    .atTime(12, 30)
                    .atZone(clock.zone)
                    .toEpochSecond()
                    .toDouble()
            plan.deferred += DeferredFeed("d_${day.toString().replace("-", "")}_45000", fireAt, 1, null, null)
        }
        return plan
    }

    // --- seven days of history --------------------------------------------------------------------

    private val episodes = mutableListOf<Pair<Double, () -> Unit>>()

    private fun at(
        ts: Double,
        block: () -> Unit,
    ) {
        episodes += ts to block
    }

    private fun history() {
        val now = clock.now()
        val horizon = now - 90
        val today = clock.today()
        for (offset in 6 downTo 0) {
            val day = today.minusDays(offset.toLong())
            val (start, _) = clock.dayBounds(day)

            fun time(
                fromHour: Double,
                toHour: Double,
            ) = start + rng.nextDouble(fromHour, toHour) * 3600
            planVisits(offset, ::time, horizon)
            planMeals(day, offset, start, horizon)
            // Two to four drinks per cat at the W7H, which recognises them.
            world.pets.values.forEach { pet ->
                repeat(rng.nextInt(2, 5)) {
                    val ts = time(6.0, 23.0)
                    val duration = rng.nextInt(8, 60)
                    if (ts < horizon) at(ts) { world.drinkCard(dev(W7H), ts, duration, pet.id) }
                }
            }
            // The T5's camera sees a cat pass by without using the box.
            repeat(rng.nextInt(1, 3)) {
                val ts = time(9.0, 22.0)
                val pet = world.pets.keys.random(rng)
                if (ts < horizon) at(ts) { world.petAppearedCard(dev(T5), ts, pet) }
            }
            // The Pura Max cleans every night at 03:00 (its `schedule` entry).
            val periodic = start + 3 * 3600 + rng.nextInt(0, 40)
            if (periodic + 95 < horizon) {
                at(periodic) {
                    val (s, d) = world.cleaningRows(dev(T4), periodic, periodic + 95, Reason.PERIODIC)
                    world.cleaningCard(s, d)
                }
            }
            if (offset == 3) {
                val err = start + 14 * 3600 + 300
                at(err) { world.errorCard(dev(T3), err, "bin full") }
                at(err + 9000) { world.errorClearedCard(dev(T3), err + 9000) }
                val deep = start + 10 * 3600
                at(deep) {
                    world.deepCleanCard(dev(W7H), deep, deep + 1500, Reason.AUTO)
                    dev(W7H)["state.lastDeepClean"] = world.iso(deep + 1500)
                }
            }
        }
        episodes.sortedBy { it.first }.forEach { it.second() }
        finishState(today)
    }

    private fun dev(id: Int) = world.devices.getValue(id)

    private fun planVisits(
        offset: Int,
        time: (Double, Double) -> Double,
        horizon: Double,
    ) {
        val litterBoxes = listOf(T3, T4, T5, T6, T7)
        world.pets.values.forEach { pet ->
            val boxes = pet.deviceIds.filter { it in litterBoxes }
            repeat(rng.nextInt(3, 6)) { n ->
                val end = time(6.0, 23.6)
                val box = boxes.random(rng)
                val duration = rng.nextInt(35, 190)
                val weight = (pet.weightG * (1 + rng.nextDouble(-0.02, 0.02))).roundToInt()
                val waste = rng.nextInt(15, 60)
                val attributed = rng.nextDouble() > 0.1
                val alert = offset == 2 && pet.id == 2 && n == 0
                if (end >= horizon) return@repeat
                at(end - duration) {
                    val device = dev(box)
                    val started = world.visitStarted(device, end - duration + 5, if (attributed) pet.id else null)
                    val card = world.visitFinished(device, started, end, duration, weight, waste, alert, videoPending = false)
                    val auto = device["settings.autoWork"]?.let(MockDevice::truthy) == true
                    val cleanAt = end + (device.number("settings.stillTime") ?: 300.0)
                    if (auto && cleanAt + 90 < horizon) attachCleaning(device, card, cleanAt)
                }
            }
        }
    }

    private fun attachCleaning(
        device: MockDevice,
        card: Card,
        at: Double,
    ) {
        val (s, d) = world.cleaningRows(device, at, at + rng.nextInt(70, 110), Reason.AUTO)
        card.steps += listOf(s.id, d.id)
    }

    private fun planMeals(
        day: LocalDate,
        offset: Int,
        start: Double,
        horizon: Double,
    ) {
        val deviceDay = deviceDay(day.dayOfWeek)
        listOf(D4, D4S, D4H, D4SH).forEach { id ->
            val device = dev(id)
            val plan = device.plan ?: return@forEach
            // The paused Gemini served nothing for the last two days.
            if (!plan.active && offset <= 1) return@forEach
            plan.groups.filter { deviceDay in it.days }.flatMap { it.meals }.forEach { meal ->
                val ts = start + meal.t + rng.nextInt(2, 9)
                val portions = if (plan.dual) (meal.a1 ?: 0) + (meal.a2 ?: 0) else meal.a ?: 0
                if (ts < horizon && portions > 0) at(ts) { world.feedCard(device, ts, portions, FeedSource.SCHEDULE) }
            }
        }
    }

    /** What the latest history leaves on each device's sensors. */
    private fun finishState(today: LocalDate) {
        val (todayStart, _) = clock.dayBounds(today)
        val cards = world.timeline.sorted()
        world.devices.values.forEach { device ->
            val mine = cards.filter { it.deviceId == device.id }
            val visits = mine.filter { it.kind == "visit" }
            visits.firstOrNull()?.let { v ->
                device["state.lastVisit"] = world.iso(v.ts)
                device["state.petWeight"] = v.weightG
                device["state.lastPet"] = v.petId?.let { world.pets[it]?.name }
            }
            if (device.isLitter) {
                val cleanings =
                    world.timeline.rows.values
                        .filter { it.deviceId == device.id && it.eventType == "5" }
                cleanings.maxByOrNull { it.ts }?.let { device["state.lastClean"] = world.iso(it.ts) }
                // A T4 sends `usedTimes` and never fills it (petkit-local's const.py): always 0.
                device["state.usedTimes"] = if (device.type == "t4") 0 else visits.count { it.ts >= todayStart }
                device["state.totalTime"] = visits.filter { it.ts >= todayStart }.sumOf { it.durationSec ?: 0 }
            }
            if (device.isFeeder) {
                val meals = mine.filter { it.kind == "feeding" && it.ts >= todayStart }
                val portions =
                    meals.sumOf { c ->
                        world.timeline.rows[c.id]
                            ?.fields
                            ?.firstOrNull { it.key == "amount" }
                            ?.text
                            ?.toInt() ?: 0
                    }
                device["state.feedState.times"] = meals.size
                device["state.feedState.realAmountTotal"] = portions * GRAMS_PER_PORTION
                mine.firstOrNull { it.kind == "feeding" }?.let { device["state.lastFeed"] = world.iso(it.ts) }
                if (device.has("amount_eaten")) device["state.feedState.eatAmountTotal"] = (portions * GRAMS_PER_PORTION * 0.8).roundToInt()
                if (device.isDualHopper) {
                    device["state.feedState.planAmountTotal1"] = meals.size
                    device["state.feedState.planRealAmountTotal1"] = meals.size
                    device["state.feedState.planAmountTotal2"] = meals.size - 1
                    device["state.feedState.planRealAmountTotal2"] = meals.size - 1
                }
            }
            mine.firstOrNull { it.kind == "drinking" }?.let {
                device["state.lastDrink"] = world.iso(it.ts)
                device["state.lastPetDetect"] = world.iso(it.ts)
            }
            if (device.model.isCamera) {
                val clip = mine.firstNotNullOfOrNull { it.media?.highlight }
                device["state.lastClipPath"] = clip
            }
        }
        world.pets.values.forEach { pet ->
            // Mochi's learned weight is the median of her recent visits, as petkit-local learns it.
            if (pet.weightLearned) {
                val weights =
                    cards
                        .filter { it.kind == "visit" && it.petId == pet.id }
                        .mapNotNull { it.weightG }
                        .take(10)
                        .sorted()
                if (weights.isNotEmpty()) pet.learnedWeightG = weights[weights.size / 2].toDouble()
            }
        }
    }

    companion object {
        const val T3 = 1001
        const val T4 = 1002
        const val T5 = 1003
        const val T6 = 1004
        const val T7 = 1005
        const val D4 = 2001
        const val D4S = 2002
        const val D4H = 2003
        const val D4SH = 2004
        const val W7H = 3001
        const val W5 = 3002
        const val CTW3 = 3003
        const val K3 = 4001

        val DEVICES =
            listOf(
                SeedDevice(T3, "t3", "20230612T3000417", "1.322"),
                SeedDevice(T4, "t4", "20240305T4001288", "1.652"),
                SeedDevice(T5, "t5", "20250117T5000093", "1.4.12"),
                SeedDevice(T6, "t6", "20250402T6000211", "1.2.8"),
                SeedDevice(T7, "t7", "20250820T7000035", "1.0.16"),
                SeedDevice(D4, "d4", "20220914D4000654", "1.267"),
                SeedDevice(D4S, "d4s", "20230225D4S00187", "1.348"),
                SeedDevice(D4H, "d4h", "20240711D4H00402", "1.867"),
                SeedDevice(D4SH, "d4sh", "20240903DSH00128", "1.867"),
                SeedDevice(W7H, "w7h", "20250610W7H00077", "1.263"),
                SeedDevice(W5, "w5", null, "1.18"),
                SeedDevice(CTW3, "ctw3", null, "2.06"),
                SeedDevice(K3, "k3", null, "1.03"),
            )

        /** Each Bluetooth accessory and the device relaying it, in the order the parent lists them. */
        val ACCESSORIES = listOf(K3 to T4, W5 to D4SH, CTW3 to D4)

        /** The embedded-Linux models reach the broker; the ESP32 ones and the BLE accessories poll HTTP. */
        fun transportOf(type: String) = if (type in setOf("t5", "t6", "t7", "d4h", "d4sh", "w7h")) "mqtt" else "http"

        /** The device's own day numbers: 1 = Sunday ... 7 = Saturday. */
        fun deviceDay(day: DayOfWeek): Int = day.value % 7 + 1

        /** Switches that are off on a device out of the box. */
        private val OFF_BY_DEFAULT =
            setOf(
                "manualLock",
                "kitten",
                "disturbMode",
                "deepClean",
                "underweight",
                "bury",
                "downpos",
                "sandSaving",
                "cameraOff",
                "cameraInward",
                "noSound",
                "homeMode",
                "toneMode",
                "awDisturbMode",
                "wlDisturbMode",
                "deepSpray",
                "fixedTimeSpray",
                "moveDetection",
                "microLight",
                "childLock",
                "isLock",
                "noDisturbingSwitch",
                "dndState",
                "wanderDetection",
            )

        private val BASE_VALUES: Map<String, Any?> =
            mapOf(
                // litter
                "state.workingState" to -1,
                "state.sandWeight" to 5240,
                "state.sandPercent" to 82,
                "state.usedTimes" to 0,
                "state.deodorantLeftDays" to 21,
                "state.sprayLeftDays" to 17,
                "state.totalTime" to 0,
                "state.rssi" to -54,
                "state.boxState" to 1,
                // An N60 is in the box: a real T5 reports sprayState 1 in every captured snapshot,
                // and only the camera boxes (T5, T6, T7) publish the entity that reads it.
                "state.sprayState" to 1,
                "settings.stillTime" to 300,
                "settings.sandType" to 1,
                "settings.autoIntervalMin" to 0,
                "settings.volume" to 5,
                // feeders
                "state.desiccantLeftDays" to 18,
                "state.food" to 2,
                "state.food1" to 2,
                "state.food2" to 0,
                "state.bowl" to 35,
                "state.weight" to 18,
                "state.batV" to 5110,
                "state.DCV" to 5020,
                "state.door" to 0,
                "state.ir_b_1" to 0,
                "state.ir_b_2" to 0,
                "state.ir_c" to 1,
                "state.home_hall" to 1,
                "state.batteryPower" to 1,
                "settings.surplusControl" to 60,
                "settings.selectedSound" to 0,
                "local.feedAmount1" to 1,
                "local.feedAmount2" to 1,
                "local.feedScheduleActive" to true,
                // camera models: petkit-local records the media each enabled capability uploads
                "capabilities.fullVideo" to true,
                "capabilities.eventImage" to true,
                "capabilities.highLight" to true,
                "capabilities.dynamicVideo" to true,
                // W7H
                "state.heatRealTemp" to 24.5,
                "state.pumpState" to 1,
                "state.stgInstall" to 1,
                "state.cwtInstall" to 1,
                "state.wtInstall" to 1,
                "state.wtLock" to 1,
                "state.heatInstall" to 1,
                "state.sw" to 1,
                "state.hall_CL" to 1,
                "state.hall_CKL" to 1,
                "state.hall_CKR" to 1,
                "state.hall_LTD" to 1,
                "state.hall_TY" to 1,
                "state.cwtState" to 2,
                "state.wtState" to 0,
                "state.rebootReason" to "power on",
                "state.disinfectState" to 0,
                "state.lastDeepCleanResult" to 0,
                "settings.fountainTime" to 3,
                "settings.sleepTime" to 10,
                "settings.fountainMode" to 1,
                "settings.waterChangeCycle" to 3,
                "settings.flushCycle" to 1,
                "settings.language" to "en_US",
                "settings.waterChangeTime" to 36000,
                "settings.flushTime" to 32400,
                // T6
                "state.packState" to 0,
                "state.packageCount" to 7,
                "state.packageState" to 1,
                "state.sealDoorState" to 0,
                "state.boxStoreState" to 1,
                "state.baggingState" to 0,
                "state.cameraPosition" to 0,
                // W5 / CTW3 (BLE, relayed by a parent)
                "consumables.filterPercentage" to 64,
                "consumables.filterPercent" to 41,
                "states.powerStatus" to 1,
                "states.suspendStatus" to 1,
                "states.mode" to 1,
                "states.runningStatus" to 1,
                "states.runStatus" to 1,
                "states.lampRingSwitch" to 1,
                "states.lampRingBrightness" to 80,
                "states.lightSwitch" to 1,
                "states.brightness" to 2,
                "states.smartWorkingTime" to 3,
                "states.smartSleepTime" to 5,
                "states.energyInterval" to 60,
                "states.sleepTime" to 600,
                "states.pumpRuntime" to 1056887,
                "states.waterPumpRunTime" to 832211,
                "states.todayPumpRunTime" to 14400,
                "states.batteryPercent" to 76,
                "states.batteryVoltage" to 3890,
                "states.supplyVoltage" to 5020,
                "states.electricStatus" to 2,
                "states.moduleStatus" to 0,
                // K3
                "consumables.liquid" to 63,
                "consumables.battery" to 88,
            )

        private val PER_TYPE: Map<String, Map<String, Any?>> =
            mapOf(
                "t3" to mapOf("state.sandWeight" to 4100, "state.sandPercent" to 64, "state.rssi" to -61, "state.deodorantLeftDays" to 9),
                "t4" to mapOf("state.sandWeight" to 6120, "state.sandPercent" to 91, "state.sprayLeftDays" to 4),
                "t6" to mapOf("state.sandPercent" to 47, "state.sandWeight" to 3050),
                "d4" to mapOf("state.desiccantLeftDays" to 3, "state.rssi" to -67),
                "d4sh" to mapOf("settings.selectedSound" to 2),
                "ctw3" to mapOf("states.mode" to 2),
            )
    }
}
