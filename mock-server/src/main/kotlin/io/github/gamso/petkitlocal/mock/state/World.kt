package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.MockClock
import io.github.gamso.petkitlocal.mock.MockConfig
import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.MockJson
import io.github.gamso.petkitlocal.mock.api.NotificationSettings
import io.github.gamso.petkitlocal.mock.api.PetDetail
import io.github.gamso.petkitlocal.mock.api.PetStats
import io.github.gamso.petkitlocal.mock.api.ServerKind
import io.github.gamso.petkitlocal.mock.api.TimelineItemDto
import io.github.gamso.petkitlocal.mock.api.TimelinePage
import io.github.gamso.petkitlocal.mock.api.UpdateDevice
import io.github.gamso.petkitlocal.mock.api.UpdateDeviceRemoved
import io.github.gamso.petkitlocal.mock.api.UpdateHello
import io.github.gamso.petkitlocal.mock.api.UpdateState
import io.github.gamso.petkitlocal.mock.api.UpdateTimeline
import io.github.gamso.petkitlocal.mock.catalog.Catalog
import io.github.gamso.petkitlocal.mock.seed.Seed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import kotlin.random.Random

/** A media file the fake tree holds: a generated JPEG or the bundled clip. */
data class MediaEntry(
    val video: Boolean,
    val seed: Int,
)

/** "Answer the next N requests to this route with this error" — set by the control API. */
data class ForcedFailure(
    val method: String,
    val path: String,
    val status: Int,
    val error: String,
    val message: String,
    val retryAfter: Int?,
    var remaining: Int,
)

/**
 * The whole fake petkit-local: devices, pets, history, sessions and the open sockets.
 *
 * Every read and write holds [lock]; nothing inside it suspends. Delayed effects (a cleaning
 * that ends, a recording that arrives) are coroutines in [scope] that take the lock when they
 * fire; [reset] cancels them all.
 */
class World(
    val config: MockConfig,
) {
    val lock = Any()
    val clock = MockClock(config.timezone)
    val catalog = Catalog.load()
    val hub = UpdateHub()

    /** Live video: the test pattern's sockets and HLS views. */
    val live = LiveHub()
    val auth =
        Auth(clock, config.lockoutBaseSec, config.lockoutMaxSec) { ended ->
            hub.end(Subscriber.CLOSE_SESSION_ENDED, "session ended") { it.session === ended }
            live.endSessions { it.id == ended.id }
        }
    val devices = TreeMap<Int, MockDevice>()
    val pets = TreeMap<Int, Pet>()
    val timeline = Timeline()
    val media = HashMap<String, MediaEntry>()
    val failures = mutableListOf<ForcedFailure>()

    /** Notification kinds switched off in the "panel" (control API): sent to no phone. */
    val disabledKinds = mutableSetOf<String>()

    /** The scenario engine's dice; history has its own, so both replay the same for one seed. */
    var random = Random(config.seed + 1)
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = LoggerFactory.getLogger(World::class.java)

    init {
        reset()
    }

    /** Back to the seed: devices, pets, seven days of history, the configured password, no sessions. */
    fun reset() =
        synchronized(lock) {
            scope.coroutineContext.cancelChildren()
            clock.reset()
            auth.setPasswordFromPanel(config.password)
            auth.clearThrottle()
            devices.clear()
            pets.clear()
            timeline.clear()
            media.clear()
            failures.clear()
            disabledKinds.clear()
            live.reset()
            random = Random(config.seed + 1)
            Seed(this, Random(config.seed)).build()
            log.info("world reset: {} devices, {} pets, {} timeline cards", devices.size, pets.size, timeline.cards.size)
        }

    /** `NotificationSettings` for [session]: no notification socket here, so never connected. */
    fun notificationSettings(session: AppSession) =
        synchronized(lock) {
            NotificationSettings(
                kinds = session.kinds,
                serverKinds = AppSession.NOTIFICATION_KINDS.map { ServerKind(it, it !in disabledKinds) },
                connected = false,
                lastId = 0,
            )
        }

    fun shutdown() {
        hub.end(Subscriber.CLOSE_GOING_AWAY, "server shutting down")
        live.end(LiveViewer.CLOSE_GOING_AWAY, "server shutting down")
        scope.cancel()
    }

    // --- emission ---------------------------------------------------------------------------------

    fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = MockJson.encodeToString(serializer, value)

    /**
     * Runs [block] against [device] and tells every socket what changed: a `device` message when
     * the summary (other than `last_seen`) did, a `state` message with only the entities whose
     * value did. [reported] marks it as the device's own report, which moves `last_seen`.
     */
    fun <T> change(
        device: MockDevice,
        reported: Boolean = true,
        block: () -> T,
    ): T {
        val before = device.changes()
        // An accessory's summary follows its parent's (online, transport): watch them too.
        val watched = listOf(device) + device.accessories
        val summariesBefore = watched.map { it.summary().copy(lastSeen = null) }
        val result = block()
        if (reported && device.online) device.lastSeen = clock.now()
        if (devices[device.id] !== device) return result
        watched.zip(summariesBefore).forEach { (watchedDevice, summaryBefore) ->
            val summaryAfter = watchedDevice.summary()
            if (summaryAfter.copy(lastSeen = null) != summaryBefore) {
                hub.broadcast(encode(UpdateDevice.serializer(), UpdateDevice(device = summaryAfter)))
            }
        }
        val changed = device.changes().values.filter { before[it.key] != it }
        if (changed.isNotEmpty()) {
            hub.broadcast(encode(UpdateState.serializer(), UpdateState(deviceId = device.id, ts = clock.now(), entities = changed)))
        }
        return result
    }

    fun publish(
        card: Card,
        replaces: List<Int> = emptyList(),
    ) {
        hub.broadcast(encode(UpdateTimeline.serializer(), UpdateTimeline(item = timeline.render(card, pets), replaces = replaces)))
    }

    fun hello(): String =
        encode(
            UpdateHello.serializer(),
            UpdateHello(serverTime = clock.now(), heartbeatSec = config.heartbeatSec, devices = devices.values.map { it.detail() }),
        )

    /** Opens a socket's feed: its `hello` is queued before any change can reach it. */
    fun attach(session: AppSession): Subscriber =
        synchronized(lock) {
            val sub = Subscriber(session, config.wsQueueLimit)
            sub.push(hello())
            hub.add(sub)
            sub
        }

    /** Runs [block] under the lock after [seconds]; cancelled by [reset]. */
    fun later(
        seconds: Double,
        block: () -> Unit,
    ): Job =
        scope.launch {
            if (seconds > 0) delay((seconds * 1000).toLong())
            synchronized(lock) { block() }
        }

    // --- devices ----------------------------------------------------------------------------------

    fun device(id: Int): MockDevice? = devices[id]

    /**
     * `mqtt` when the device holds a session now, `queued` for its next HTTP poll. An accessory's
     * write is relayed by its parent at once or refused ([requireRelay]), never queued.
     */
    fun delivered(device: MockDevice): String =
        if (device.isAccessory || (device.online && device.transport == "mqtt")) "mqtt" else "queued"

    /**
     * Refuses a write to an accessory no parent can relay now, as `send_accessory_command` does:
     * only a parent holding an MQTT session can.
     *
     * @throws ApiException 409 `accessory_unreachable`.
     */
    fun requireRelay(device: MockDevice) {
        if (!device.isAccessory) return
        val parent = device.parent ?: throw ApiException(409, "accessory_unreachable", "no registered device relays this accessory")
        if (!parent.online || parent.transport != "mqtt") {
            throw ApiException(
                409,
                "accessory_unreachable",
                "the ${parent.model.model} relaying it is not on MQTT, and an accessory is only reachable while it is",
            )
        }
    }

    /** Pairs [accessory] with [parent], listed after the ones it already relays. */
    fun link(
        accessory: MockDevice,
        parent: MockDevice,
    ) {
        accessory.parent = parent
        parent.accessories += accessory
    }

    fun setOnline(
        device: MockDevice,
        online: Boolean,
    ) = change(device) {
        device.online = online
        // The offline watchdog clears `mqtt_connected`; a device back online reconnects.
        device.transport = if (online) Seed.transportOf(device.type) else "http"
        if (online) device.lastSeen = clock.now()
    }

    fun removeDevice(id: Int): Boolean {
        val removed = devices.remove(id) ?: return false
        hub.broadcast(encode(UpdateDeviceRemoved.serializer(), UpdateDeviceRemoved(deviceId = id)))
        // The link is stated from both ends, so both ends hear of it (`device` messages).
        removed.parent?.let { parent ->
            parent.accessories.remove(removed)
            hub.broadcast(encode(UpdateDevice.serializer(), UpdateDevice(device = parent.summary())))
        }
        removed.accessories.forEach { accessory ->
            accessory.parent = null
            hub.broadcast(encode(UpdateDevice.serializer(), UpdateDevice(device = accessory.summary())))
        }
        removed.parent = null
        removed.accessories.clear()
        return true
    }

    // --- timeline ---------------------------------------------------------------------------------

    fun timelinePage(
        deviceId: Int?,
        day: LocalDate?,
        kind: String,
        petId: Int?,
        limit: Int,
        cursor: Pair<Double, Int>?,
    ): TimelinePage {
        var cards = timeline.sorted().asSequence()
        if (deviceId != null) cards = cards.filter { it.deviceId == deviceId }
        if (day != null) {
            val (start, end) = clock.dayBounds(day)
            cards = cards.filter { it.ts >= start && it.ts < end }
        }
        if (petId != null) cards = cards.filter { it.petId == petId }
        cards = cards.filter { it.matches(kind) }
        if (cursor != null) cards = cards.filter { it.ts < cursor.first || (it.ts == cursor.first && it.id < cursor.second) }
        val window = cards.take(limit + 1).toList()
        val page = window.take(limit)
        val next = if (window.size > limit) Timeline.encodeCursor(page.last().ts, page.last().id) else null
        return TimelinePage(items = page.map { timeline.render(it, pets) }, nextCursor = next)
    }

    // --- pets -------------------------------------------------------------------------------------

    private fun visitsOf(petId: Int): List<Card> = timeline.sorted().filter { it.kind == "visit" && it.petId == petId }

    fun petDetail(pet: Pet): PetDetail {
        val visits = visitsOf(pet.id)
        val weighed = visits.mapNotNull { it.weightG?.toDouble() }.take(MEDIAN_OF)
        val (todayStart, todayEnd) = clock.dayBounds(clock.today())
        val last = visits.firstOrNull()
        val since = clock.now() - 7 * 86400
        val dto = pet.dto()
        return PetDetail(
            id = dto.id,
            name = dto.name,
            deviceIds = dto.deviceIds,
            enteredWeightG = dto.enteredWeightG,
            weightG = dto.weightG,
            weightLearned = dto.weightLearned,
            photo = dto.photo,
            stats =
                PetStats(
                    medianWeightG = weighed.takeIf { it.isNotEmpty() }?.sorted()?.let { it[it.size / 2] },
                    visitsToday = visits.count { it.ts >= todayStart && it.ts < todayEnd },
                    lastVisitTs = last?.displayTs,
                    lastVisitWeightG = last?.weightG?.toDouble(),
                    lastVisitDurationSec = last?.durationSec?.toDouble(),
                    lastDeviceId = last?.deviceId,
                ),
            recentVisits = visits.filter { it.ts >= since }.take(RECENT_VISITS).map { timeline.render(it, pets) },
        )
    }

    fun render(card: Card): TimelineItemDto = timeline.render(card, pets)

    // --- helpers shared by the seed, the scenario engine and the writes -----------------------------

    fun iso(ts: Double): String = clock.localDateTime(ts).withZoneSameInstant(java.time.ZoneOffset.UTC).format(ISO)

    fun fieldTime(ts: Double): String = clock.localDateTime(ts).format(FIELD_TIME)

    /** Registers the media of a card and returns the paths, `<device>/<day>/<card>_<slot>.<ext>`. */
    fun mediaFor(
        device: MockDevice,
        cardId: Int,
        ts: Double,
        slots: List<String>,
    ): Map<String, String> {
        val day = clock.localDateTime(ts).toLocalDate()
        return slots.associateWith { slot ->
            val video = slot in VIDEO_SLOTS
            val rel = "${device.id}/$day/${cardId}_$slot.${if (video) "mp4" else "jpg"}"
            media[rel] = MediaEntry(video, cardId + slot.hashCode())
            rel
        }
    }

    companion object {
        const val MEDIAN_OF = 10
        const val RECENT_VISITS = 20
        val VIDEO_SLOTS = setOf("highlight", "playback", "timelapse")
        private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
        private val FIELD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
    }
}
