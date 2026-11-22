package org.petkitlocal.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationSubject
import org.petkitlocal.core.domain.notifications.ServerNotification
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val PURA_MAX = Device(
    id = 1002,
    type = "t4",
    modelName = "Pura Max",
    family = DeviceFamily.LitterBox,
    online = true,
    transport = DeviceTransport.Http,
    lastSeen = null,
    firmware = null,
    isCamera = false,
)

// 2026-11-15 23:30 UTC, already the 16th in Paris.
private val LATE = Instant.parse("2026-11-15T23:30:00Z")

private fun notification(
    kind: String,
    code: String? = null,
    id: Long = 7,
    petId: Long? = null,
    replay: Boolean = false,
) = ServerNotification(id, replay, kind, deviceId = PURA_MAX.id, petId = petId, code = code, time = LATE)

/** The text of each kind, by the server's keys, in English and in French, on real resources. */
@RunWith(RobolectricTestRunner::class)
class NotificationTextsTest {
    private val texts get() = NotificationTexts(ApplicationProvider.getApplicationContext<Context>().resources)
    private val known = NotificationSubject(PURA_MAX, petName = "Mochi")
    private val unknown = NotificationSubject(device = null, petName = null)

    @Test
    fun `every kind has its English text, naming the device and the pet`() {
        assertEquals(
            NotificationText("Mochi used the litter box", "Pura Max"),
            texts.of(notification("toilet", "10"), known),
        )
        assertEquals(NotificationText("Litter box visit", "A device"), texts.of(notification("toilet", "10"), unknown))
        assertEquals("Meal served", texts.of(notification("feed_served"), known).title)
        assertEquals(
            NotificationText("Meal failed", "Pura Max could not serve a meal."),
            texts.of(notification("feed_failed"), known),
        )
        assertEquals(
            NotificationText("Device fault", "Pura Max reports a fault."),
            texts.of(notification("error", "errorMsg"), known),
        )
        assertEquals(
            NotificationText("Device offline", "Pura Max stopped answering the server."),
            texts.of(notification("offline"), known),
        )
        assertEquals("Hopper 2 of Pura Max is empty.", texts.of(notification("food_low", "food2"), known).text)
        assertEquals("The hopper of Pura Max is empty.", texts.of(notification("food_low", "foodLow"), known).text)
        assertEquals(
            NotificationText("Waste bin full", "Empty the waste bin of Pura Max."),
            texts.of(notification("consumable", "boxFull"), known),
        )
        assertEquals("Litter low", texts.of(notification("consumable", "sandPercent"), known).title)
    }

    @Test
    fun `a key this app does not know yet still gives a notification`() {
        assertEquals("Something to see to", texts.of(notification("consumable", "newPart"), known).title)
        assertEquals("The hopper of Pura Max is empty.", texts.of(notification("food_low", "food3"), known).text)
        assertEquals(
            NotificationText("Alert from your server", "Pura Max: open Patoune to see it."),
            texts.of(notification("a_later_kind"), known),
        )
    }

    @Test
    @Config(qualifiers = "fr")
    fun `in French, by the same keys`() {
        assertEquals(
            NotificationText("Passage de Mochi à la litière", "Pura Max"),
            texts.of(notification("toilet", "10"), known),
        )
        assertEquals(
            NotificationText("Passage à la litière", "Un appareil"),
            texts.of(notification("toilet", "10"), unknown),
        )
        assertEquals("Repas servi", texts.of(notification("feed_served"), known).title)
        assertEquals(
            NotificationText("Panne d'appareil", "Pura Max signale une panne."),
            texts.of(notification("error"), known),
        )
        assertEquals("Le réservoir 1 de Pura Max est vide.", texts.of(notification("food_low", "food1"), known).text)
        assertEquals(
            NotificationText("Filtre à remplacer", "Remplacez le filtre de Pura Max."),
            texts.of(notification("consumable", "filterWarning"), known),
        )
        assertEquals("Appareil hors ligne", texts.of(notification("offline"), known).title)
    }
}

/**
 * What the service posts: each alert on its kind's channel, opening its page; the connection on
 * its own minimum-importance channel; nothing without Android's permission.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPosterTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val poster = NotificationPoster(context) { ZoneId.of("Europe/Paris") }
    private val subject = NotificationSubject(PURA_MAX, petName = "Mochi")

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.ensure(context)
    }

    @Test
    fun `one channel per kind, and the connection's at minimum importance`() {
        val channels = manager.notificationChannels.associate { it.id to it.importance }

        assertEquals(NotificationManager.IMPORTANCE_MIN, channels[NotificationChannels.CONNECTION])
        assertEquals(NotificationManager.IMPORTANCE_LOW, channels["kind_toilet"])
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channels["kind_feed_failed"])
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels["kind_consumable"])
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels[NotificationChannels.OTHER])
        assertEquals(9, channels.size)
        assertEquals("Litter box visits", manager.getNotificationChannel("kind_toilet").name)
    }

    @Test
    fun `an alert goes to its kind's channel, opens its History entry, and replaces itself if sent again`() {
        poster.show(notification("toilet", "10", id = 42, petId = 1), subject, silent = false)
        poster.show(notification("toilet", "10", id = 42, petId = 1), subject, silent = false)

        val posted = shadowOf(manager).activeNotifications.single()
        assertEquals(NotificationPoster.TAG, posted.tag)
        assertEquals(42, posted.id)
        val alert = posted.notification
        assertEquals("kind_toilet", alert.channelId)
        assertEquals("Mochi used the litter box", alert.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(LATE.toEpochMilli(), alert.`when`)
        val intent = shadowOf(alert.contentIntent).savedIntent
        assertEquals("petkitlocal://open/history/1002/2026-11-16", intent.dataString)
        assertEquals(context.packageName, intent.`package`)
    }

    @Test
    fun `a condition opens the device, and an unknown kind goes to the other channel`() {
        val consumable = poster.build(notification("consumable", "boxFull"), subject, silent = true)
        val later = poster.build(notification("a_later_kind"), subject, silent = false)

        assertEquals("petkitlocal://open/device/1002", shadowOf(consumable.contentIntent).savedIntent.dataString)
        assertEquals("kind_consumable", consumable.channelId)
        assertEquals(NotificationChannels.OTHER, later.channelId)
    }

    @Test
    fun `the ongoing notification says where the connection stands`() {
        poster.connection(NotificationConnection.Connected(gap = true))

        val ongoing = shadowOf(manager).getNotification(NotificationPoster.ONGOING_ID)
        assertEquals(NotificationChannels.CONNECTION, ongoing.channelId)
        assertEquals("Notifications on", ongoing.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertTrue(ongoing.extras.getString(NotificationCompat.EXTRA_TEXT)!!.contains("were lost"))
        assertTrue(ongoing.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `without Android's permission nothing is posted`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        poster.show(notification("error"), subject, silent = false)
        poster.connection(NotificationConnection.Connecting)

        assertEquals(0, shadowOf(manager).activeNotifications.size)
    }
}
