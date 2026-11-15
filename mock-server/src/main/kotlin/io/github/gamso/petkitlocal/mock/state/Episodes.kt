package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.EventField
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The rows and cards each kind of episode leaves in the history. Labels, codes and decoded fields
 * are petkit-local's (`events/codes.py`, `events/decode.py`) for the same reports: a visit summary
 * is code 10 "Toilet visit", a cleaning cycle 3 then 5 "Auto cleaning completed", a meal 4
 * "Feeding completed", a drink 5 then 6 "Drinking done". HTTP event codes are per device category,
 * which is why every row carries its device type.
 *
 * Used three ways: the seed writes finished episodes into the past, the scenario engine and the
 * control API play them live (and publish each step on /ws), and actions end in one.
 */

/** How a cleaning or a feed was started, as `decode.py` words it. */
enum class Reason(
    val code: Int,
    val label: String,
    val word: String,
) {
    AUTO(0, "Auto", "Auto"),
    PERIODIC(1, "Periodic", "Periodic"),
    MANUAL(2, "Manual (app)", "Manual"),
}

private fun f(
    key: String,
    label: String,
    text: String,
) = EventField(key, label, text)

private fun kg(grams: Int) = String.format(Locale.ROOT, "%.2f kg", grams / 1000.0)

fun World.row(
    device: MockDevice,
    ts: Double,
    eventType: String,
    kind: String,
    label: String,
    petId: Int? = null,
    fields: List<EventField> = emptyList(),
    facts: List<String> = emptyList(),
    detail: Boolean = false,
): EventRow = timeline.add(EventRow(timeline.newId(), device.id, device.type, ts, eventType, kind, label, petId, fields, facts, detail))

fun World.card(
    anchor: EventRow,
    kind: String,
    displayTs: Double = anchor.ts,
    durationSec: Int? = null,
    weightG: Int? = null,
    media: CardMedia? = null,
    steps: List<Int> = emptyList(),
    healthAlert: Boolean = false,
): Card =
    timeline.add(
        Card(
            id = anchor.id,
            kind = kind,
            deviceId = anchor.deviceId,
            deviceType = anchor.deviceType,
            ts = anchor.ts,
            displayTs = displayTs,
            eventType = anchor.eventType,
            eventKind = anchor.eventKind,
            label = anchor.label,
            petId = anchor.petId,
            durationSec = durationSec,
            weightG = weightG,
            facts = anchor.facts,
            media = media,
            steps = steps.toMutableList(),
            healthAlert = healthAlert,
        ),
    )

// --- litter box -----------------------------------------------------------------------------------

/** The pet stepped on the scale: an episode still in progress, keyed on this report. */
fun World.visitStarted(
    device: MockDevice,
    ts: Double,
    petId: Int?,
): Card {
    val weigh = row(device, ts, "9", Kinds.TOILET, "Weight check", petId, detail = true)
    return card(weigh, "visit")
}

/** The visit summary that closes the episode; the in-progress card becomes one of its steps. */
fun World.visitFinished(
    device: MockDevice,
    started: Card,
    ts: Double,
    durationSec: Int,
    weightG: Int,
    wasteG: Int,
    healthAlert: Boolean,
    videoPending: Boolean,
): Card {
    val enteredAt = ts - durationSec
    val summary =
        row(
            device,
            ts,
            "10",
            Kinds.TOILET,
            "Toilet visit",
            started.petId,
            listOf(
                f("shit_weight", "Waste weight", "$wasteG g"),
                f("petWeight", "Pet weight", kg(weightG)),
                f("time_in", "Entered", fieldTime(enteredAt)),
                f("time_out", "Left", fieldTime(ts)),
            ) + if (healthAlert) listOf(f("petVoice", "petVoice", "1")) else emptyList(),
        )
    timeline.cards.remove(started.id)
    return card(
        summary,
        "visit",
        displayTs = enteredAt,
        durationSec = durationSec,
        weightG = weightG,
        media = visitMedia(device, summary.id, ts, videoPending),
        steps = listOf(started.id),
        healthAlert = healthAlert,
    )
}

private fun World.visitMedia(
    device: MockDevice,
    cardId: Int,
    ts: Double,
    videoPending: Boolean,
): CardMedia? {
    if (!device.model.isCamera) return null
    val slots = mutableListOf("snapshot", "highlight", "timelapse")
    if (!videoPending) slots += "playback"
    val waste = if (device.type in setOf("t6", "t7")) listOf("waste") else emptyList()
    val paths = mediaFor(device, cardId, ts, slots + waste)
    return CardMedia(
        snapshot = paths["snapshot"],
        highlight = paths["highlight"],
        playback = paths["playback"],
        timelapse = paths["timelapse"],
        waste = listOfNotNull(paths["waste"]),
        videoPending = videoPending,
    )
}

/** The recording of a visit was assembled: the card changes, its id does not. */
fun World.videoArrived(
    device: MockDevice,
    card: Card,
) {
    val current = card.media ?: return
    val paths = mediaFor(device, card.id, card.ts, listOf("playback"))
    card.media = current.copy(playback = paths["playback"], videoPending = false)
}

/** A cleaning cycle's two reports: 3 "Mechanism started" (a detail) and the completion. */
fun World.cleaningRows(
    device: MockDevice,
    start: Double,
    end: Double,
    reason: Reason,
    eventType: String = "5",
    what: String = "cleaning",
): Pair<EventRow, EventRow> = cleaningStart(device, start, reason) to cleaningDone(device, end, reason, eventType, what)

fun World.cleaningStart(
    device: MockDevice,
    ts: Double,
    reason: Reason,
): EventRow =
    row(
        device,
        ts,
        "3",
        Kinds.CLEANING,
        "Mechanism started",
        fields = listOf(f("start_reason", "Start reason", reason.label)),
        detail = true,
    )

/** The completion report; `eventType` 5 cleaning, 6 litter emptied, 8 deodorizing, 12 levelling, 7 reset. */
fun World.cleaningDone(
    device: MockDevice,
    ts: Double,
    reason: Reason,
    eventType: String = "5",
    what: String = "cleaning",
): EventRow {
    val litter = device.number("state.sandPercent")?.roundToInt()?.takeIf { eventType == "5" }
    val fields =
        listOf(f("start_reason", "Start reason", reason.label), f("result", "Result", "completed")) +
            listOfNotNull(litter?.let { f("litter_percent", "Litter level", "$it%") })
    val label = if (eventType == "7") "Reset done" else "${reason.word} $what completed"
    return row(device, ts, eventType, Kinds.CLEANING, label, fields = fields, facts = listOfNotNull(litter?.let { "litter $it%" }))
}

/** A cleaning that ran on its own (not after a visit): a card of its own. */
fun World.cleaningCard(
    started: EventRow,
    done: EventRow,
): Card {
    timeline.cards.remove(started.id)
    return card(done, Kinds.CLEANING, steps = listOf(started.id))
}

fun World.errorCard(
    device: MockDevice,
    ts: Double,
    message: String,
): Card {
    val r = row(device, ts, "1", Kinds.ERROR, "Error - $message", fields = listOf(f("err", "Error", message)))
    return card(r, "event")
}

fun World.errorClearedCard(
    device: MockDevice,
    ts: Double,
): Card = card(row(device, ts, "2", Kinds.ERROR, "Error cleared"), "event")

/** A pet seen by a camera box without using it (code 20): the app files it under "Pet". */
fun World.petAppearedCard(
    device: MockDevice,
    ts: Double,
    petId: Int?,
): Card {
    val r = row(device, ts, "20", Kinds.PET, "Appeared", petId)
    val paths = mediaFor(device, r.id, ts, listOf("snapshot"))
    return card(r, Kinds.PET, media = CardMedia(snapshot = paths["snapshot"]))
}

// --- feeder ---------------------------------------------------------------------------------------

/** Where a portion came from, as `codes.FEED_SRC` words it. */
enum class FeedSource(
    val code: Int,
    val text: String,
) {
    SCHEDULE(1, "scheduled"),
    APP(3, "manual (app)"),
    ONE_OFF(5, "one-off"),
}

fun World.feedCard(
    device: MockDevice,
    ts: Double,
    portions: Int,
    source: FeedSource,
): Card {
    val r =
        row(
            device,
            ts,
            "4",
            Kinds.FEEDING,
            "Feeding completed",
            fields =
                listOf(
                    f("result", "Result", "completed"),
                    f("amount", "amount", "$portions"),
                    f("realAmount", "realAmount", "$portions"),
                    f("src", "Source", source.text),
                ),
        )
    val media =
        if (device.model.isCamera) {
            val paths = mediaFor(device, r.id, ts, listOf("snapshot", "highlight"))
            CardMedia(snapshot = paths["snapshot"], highlight = paths["highlight"])
        } else {
            null
        }
    return card(r, Kinds.FEEDING, media = media)
}

// --- fountain -------------------------------------------------------------------------------------

fun World.drinkCard(
    device: MockDevice,
    ts: Double,
    durationSec: Int,
    petId: Int?,
): Card {
    val started = row(device, ts - durationSec, "5", Kinds.DRINKING, "Drinking started", petId, detail = true)
    val done = row(device, ts, "6", Kinds.DRINKING, "Drinking done", petId, listOf(f("duration", "duration", "$durationSec")))
    val media =
        if (device.model.isCamera) {
            val paths = mediaFor(device, done.id, ts, listOf("snapshot", "highlight"))
            CardMedia(snapshot = paths["snapshot"], highlight = paths["highlight"])
        } else {
            null
        }
    return card(done, Kinds.DRINKING, displayTs = started.ts, durationSec = durationSec, media = media, steps = listOf(started.id))
}

fun World.deepCleanCard(
    device: MockDevice,
    start: Double,
    end: Double,
    reason: Reason,
): Card {
    val started = row(device, start, "3", Kinds.CLEANING, "Work started", detail = true)
    val done =
        row(
            device,
            end,
            "12",
            Kinds.CLEANING,
            "Deep cleaning completed",
            fields = listOf(f("start_reason", "Start reason", reason.label), f("result", "Result", "completed")),
        )
    return card(done, Kinds.CLEANING, steps = listOf(started.id))
}
