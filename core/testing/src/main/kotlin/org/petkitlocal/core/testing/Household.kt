package org.petkitlocal.core.testing

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.petkitlocal.core.domain.model.AccessoryRef
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.domain.video.RecentClip

/**
 * A household like the mock server's (mock-server/README.md): one device of each family, two
 * cats and a morning of cards, with the entity keys petkit-local really uses. For ViewModel,
 * Compose UI and screenshot tests.
 *
 * The Bluetooth accessories: the Pura Max relays the Pura Air (its K3 slot), the Fresh Element
 * Solo relays the CTW3, and the W5 is linked to no device, which is why it is offline (the mock
 * links it to the YumShare instead).
 */
@Suppress("MagicNumber") // Fixture data.
object Household {
    val zone: ZoneId = ZoneId.of("Europe/Paris")
    val today: LocalDate = LocalDate.of(2026, 11, 15)

    /** 10:00 in Paris on [today]. */
    val clock: Clock = Clock.fixed(ZonedDateTime.of(today.atTime(10, 0), zone).toInstant(), zone)

    val pets: List<Pet> = listOf(
        Pet(1, "Mochi", listOf(1002, 1003, 3001), 4200.0, 4333.0, weightLearned = true, photoUrl = null),
        Pet(2, "Tigrou", listOf(1002, 3001), 6000.0, 6000.0, weightLearned = false, photoUrl = null),
    )

    val litterBox = device(
        1002,
        "t4",
        "Pura Max",
        DeviceFamily.LitterBox,
        sensor("device_status", EntityValue.Number(-1.0), display = "idle"),
        sensor("error", EntityValue.None),
        sensor("litter_percent", EntityValue.Number(91.0), unit = "%", name = "Litter Level"),
        sensor("n50_durability", EntityValue.Number(21.0), unit = "days", name = "N50 Days Left"),
        binary("waste_bin", true, "problem", "Waste Bin Full"),
        binary("pet_occupied", false, "occupancy", "Pet Occupied"),
        actions = listOf(DeviceAction("cleaning_start", "Start Cleaning", false)),
        accessories = listOf(AccessoryRef(4001, "k3", DeviceFamily.Purifier)),
    )

    val cameraLitterBox = device(
        1003,
        "t5",
        "Purobot Max Pro 2",
        DeviceFamily.LitterBox,
        sensor("device_status", EntityValue.Number(0.0), display = "cleaning"),
        sensor("error", EntityValue.None),
        transport = DeviceTransport.Mqtt,
        isCamera = true,
    )

    val feeder = device(
        2001,
        "d4",
        "Fresh Element Solo",
        DeviceFamily.Feeder,
        sensor("error", EntityValue.None),
        sensor("desiccant_days", EntityValue.Number(3.0), unit = "days", name = "Desiccant Days Left"),
        sensor("total_dispensed", EntityValue.Number(40.0), unit = "g", name = "Total Dispensed"),
        binary("food_low", false, "problem", "Food Low"),
        accessories = listOf(AccessoryRef(3003, "ctw3", DeviceFamily.Fountain)),
    )

    val dualFeeder = device(
        2004,
        "d4sh",
        "YumShare Dual-Hopper",
        DeviceFamily.Feeder,
        sensor("error", EntityValue.Text("motor stuck")),
        sensor("hopper2_level", EntityValue.Number(0.0), display = "Empty", name = "Hopper 2"),
        sensor("total_dispensed", EntityValue.Number(20.0), unit = "g"),
        transport = DeviceTransport.Mqtt,
        isCamera = true,
    )

    val cameraFountain = device(
        3001,
        "w7h",
        "EverSweet Ultra AI",
        DeviceFamily.Fountain,
        sensor("error", EntityValue.None),
        sensor("temperature", EntityValue.Number(24.5), unit = "°C"),
        transport = DeviceTransport.Mqtt,
        isCamera = true,
    )

    val offlineFountain = device(
        3002,
        "w5",
        "EverSweet 3 Pro",
        DeviceFamily.Fountain,
        binary("w5_water_missing", false, "problem", "Water Low"),
        online = false,
        connection = DeviceConnection.Ble,
    )

    val batteryFountain = device(
        3003,
        "ctw3",
        "EverSweet Max Cordless",
        DeviceFamily.Fountain,
        sensor("ctw3_filter", EntityValue.Number(12.0), unit = "%", name = "Filter"),
        binary("ctw3_water_lack", false, "problem", "Water Low"),
        connection = DeviceConnection.Ble,
        parentId = 2001,
    )

    val purifier = device(
        4001,
        "k3",
        "Pura Air K3",
        DeviceFamily.Purifier,
        sensor("k3_liquid", EntityValue.Number(63.0), unit = "%", name = "K3 Liquid"),
        sensor("k3_battery", EntityValue.Number(88.0), unit = "%", name = "K3 Battery"),
        connection = DeviceConnection.Ble,
        parentId = 1002,
    )

    /** A kind this app does not know: the generic family. */
    val unknown = device(9001, "z9", "Unknown", DeviceFamily.Other)

    val devices: List<Device> = listOf(
        litterBox,
        cameraLitterBox,
        feeder,
        dualFeeder,
        cameraFountain,
        offlineFountain,
        batteryFountain,
        purifier,
        unknown,
    )

    /** This morning's cards, newest first. */
    val todayItems: List<TimelineItem> = listOf(
        item(18, 3001, at(9, 30), TimelineCategory.Drinking, pet = 2, duration = 42),
        item(17, 2001, at(8, 0), TimelineCategory.Feeding),
        item(16, 1002, at(7, 45), TimelineCategory.Toileting, pet = 1, duration = 95),
        item(15, 2004, at(7, 0), TimelineCategory.Feeding),
        item(14, 1002, at(6, 19), TimelineCategory.Toileting, pet = 2, duration = 186),
        item(13, 1003, at(5, 10), TimelineCategory.Pet, pet = 1),
        item(12, 1003, at(4, 2), TimelineCategory.Toileting, TimelineCategory.HealthAlert, pet = 1, duration = 240),
        item(11, 1002, at(3, 0), TimelineCategory.Cleaning),
        item(10, 1002, at(1, 20), TimelineCategory.Toileting, pet = 2, duration = 60),
    )

    /**
     * The camera litter box's cards holding a clip, newest first: this morning's visit and the
     * cat it saw, then yesterday's and older visits, one only with its full recording.
     */
    val cameraClipItems: List<TimelineItem> = listOf(
        item(12, 1003, at(4, 2), TimelineCategory.Toileting, TimelineCategory.HealthAlert, pet = 1, duration = 240)
            .withClips(MediaKind.Highlight),
        item(13, 1003, at(5, 10), TimelineCategory.Pet, pet = 1)
            .copy(eventKind = "pet")
            .withClips(MediaKind.Highlight),
        item(9, 1003, at(-1, 22, 40), TimelineCategory.Toileting, pet = 2, duration = 75)
            .withClips(MediaKind.Highlight, MediaKind.Playback),
        item(8, 1003, at(-3, 7, 5), TimelineCategory.Toileting, duration = 130).withClips(MediaKind.Playback),
    ).sortedByDescending { it.ts }

    /** What the camera's page and live video show of [cameraClipItems]. */
    val cameraClips: List<RecentClip> = cameraClipItems.map { RecentClip(it, it.media!!.clips.first()) }

    fun at(hour: Int, minute: Int): Instant = ZonedDateTime.of(today.atTime(hour, minute), zone).toInstant()

    /** [hour]:[minute] on the day [days] days from today (negative: before it). */
    fun at(days: Long, hour: Int, minute: Int): Instant =
        ZonedDateTime.of(today.plusDays(days).atTime(hour, minute), zone).toInstant()

    /** This card with a snapshot and clips of [kinds], at made-up paths of the mock's media tree. */
    fun TimelineItem.withClips(vararg kinds: MediaKind): TimelineItem {
        val base = "http://10.0.2.2:8097/app/v1/media"
        val snapshot = MediaFile(MediaKind.Snapshot, "$base/$id/snapshot.jpg", "$base/thumb/$id/snapshot.jpg")
        val clips = kinds.map { kind ->
            val file = "$id/${kind.name.lowercase()}.mp4"
            MediaFile(kind, "$base/$file", "$base/thumb/$file")
        }
        return copy(media = TimelineMedia(listOf(snapshot) + clips))
    }

    @Suppress("LongParameterList")
    fun device(
        id: Long,
        type: String,
        model: String,
        family: DeviceFamily,
        vararg entities: Entity,
        online: Boolean = true,
        transport: DeviceTransport = DeviceTransport.Http,
        isCamera: Boolean = false,
        actions: List<DeviceAction> = emptyList(),
        connection: DeviceConnection = DeviceConnection.Wifi,
        parentId: Long? = null,
        accessories: List<AccessoryRef> = emptyList(),
    ) = Device(
        id = id,
        type = type,
        model = model,
        family = family,
        online = online,
        transport = transport,
        lastSeen = null,
        firmware = null,
        isCamera = isCamera,
        entities = entities.toList(),
        actions = actions,
        connection = connection,
        parentId = parentId,
        accessories = accessories,
    )

    fun sensor(
        key: String,
        value: EntityValue,
        unit: String? = null,
        display: String? = null,
        name: String = key,
        category: EntityCategory? = null,
    ) = Entity(key, name, EntityComponent.Sensor, value, display = display, unit = unit, category = category)

    fun binary(key: String, on: Boolean, deviceClass: String, name: String = key) =
        Entity(key, name, EntityComponent.BinarySensor, EntityValue.Bool(on), deviceClass = deviceClass)

    fun item(
        id: Long,
        deviceId: Long,
        at: Instant,
        vararg categories: TimelineCategory,
        pet: Long? = null,
        duration: Int? = null,
    ) = TimelineItem(
        id = id,
        kind = if (TimelineCategory.Toileting in categories) "visit" else "event",
        categories = categories.toSet(),
        deviceId = deviceId,
        deviceType = null,
        ts = at.plusSeconds(duration?.toLong() ?: 0),
        displayTs = at,
        eventType = null,
        eventKind = null,
        label = null,
        pet = pet?.let { id -> PetRef(id, pets.firstOrNull { it.id == id }?.name) },
        durationSec = duration,
        weightG = null,
        facts = emptyList(),
    )
}
