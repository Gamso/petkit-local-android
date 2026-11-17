package org.petkitlocal.core.domain.settings

import javax.inject.Inject
import org.petkitlocal.core.domain.Outcome

/**
 * The kinds of notification the server sends (`NotificationKind` in api/openapi.yaml). Kinds are
 * the server's keys: a newer server may send one this list does not have, and the app shows it
 * by its key rather than hiding it.
 */
object NotificationKinds {
    /** A litter-box visit. */
    const val TOILET = "toilet"

    /** A meal dispensed (or skipped because the bowl was not empty). */
    const val FEED_SERVED = "feed_served"

    /** A meal that failed. */
    const val FEED_FAILED = "feed_failed"

    /** A device fault. */
    const val ERROR = "error"

    /** A hopper empty. */
    const val FOOD_LOW = "food_low"

    /** A bin, tray, filter, litter, water or deodorizer to see to. */
    const val CONSUMABLE = "consumable"

    /** A device that stopped answering. */
    const val OFFLINE = "offline"

    val KNOWN: List<String> = listOf(TOILET, FEED_SERVED, FEED_FAILED, ERROR, FOOD_LOW, CONSUMABLE, OFFLINE)
}

/** One kind the server sends, and whether the panel's switch for it lets it through to any phone. */
data class ServerNotificationKind(val kind: String, val enabledOnServer: Boolean)

/**
 * This phone's notification settings, as its session holds them on the server
 * (`GET /notifications`). The server filters what it sends by [chosen]; the app never drops what
 * the server sent (ARCHITECTURE.md §5).
 *
 * @property chosen the kinds this phone receives, in the server's order; null for every kind the
 *   server sends, including kinds it adds later.
 * @property serverKinds every kind the server sends, in its order.
 * @property connected whether this session holds the notification socket now.
 * @property lastId the newest notification id the server logged, for catch-up.
 */
data class NotificationSettings(
    val chosen: List<String>?,
    val serverKinds: List<ServerNotificationKind>,
    val connected: Boolean,
    val lastId: Long,
) {
    /** Whether this phone asked for [kind]; the panel may still switch it off for everyone. */
    fun receives(kind: String): Boolean = chosen == null || kind in chosen
}

/**
 * The phone's notification kinds on the server (`/notifications`). Stored with the session: they
 * end with it, and every sign-in starts with every kind.
 */
interface NotificationSettingsRepository {
    suspend fun load(): Outcome<NotificationSettings>

    /** Replaces the choice: [kinds] null for every kind, empty for none. Answers what the server now holds. */
    suspend fun setKinds(kinds: List<String>?): Outcome<NotificationSettings>
}

class GetNotificationSettingsUseCase @Inject constructor(private val repository: NotificationSettingsRepository) {
    suspend operator fun invoke(): Outcome<NotificationSettings> = repository.load()
}

/**
 * Turns one kind on or off from [current], the settings last read from the server. The others
 * stay as they are. When every kind the server lists ends up chosen, it asks for "every kind"
 * (null) rather than for the list, so a kind the server adds later reaches this phone too.
 */
class SetNotificationKindUseCase @Inject constructor(private val repository: NotificationSettingsRepository) {
    suspend operator fun invoke(
        current: NotificationSettings,
        kind: String,
        on: Boolean,
    ): Outcome<NotificationSettings> = repository.setKinds(nextKinds(current, kind, on))

    companion object {
        fun nextKinds(current: NotificationSettings, kind: String, on: Boolean): List<String>? {
            val all = current.serverKinds.map { it.kind }
            val chosen = current.chosen ?: all
            val next = if (on) chosen + kind else chosen - kind
            if (all.isNotEmpty() && next.containsAll(all)) return null
            // The server's order first, then anything it did not list, as the server would keep it.
            return (all.filter { it in next } + next.filter { it !in all }).distinct()
        }
    }
}
