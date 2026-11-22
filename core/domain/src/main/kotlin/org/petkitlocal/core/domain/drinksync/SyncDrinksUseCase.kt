package org.petkitlocal.core.domain.drinksync

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.repository.TimelineRepository

/** Where a sync of a fountain's drink history stands, step by step, as the page shows it. */
sealed interface DrinkSyncStep {
    /** Asking petkit-local for the fountain's address and secret, and looking for it. */
    data object Searching : DrinkSyncStep

    data object Connected : DrinkSyncStep

    /** The fountain is sending the [drinks] records it holds. */
    data class Reading(val drinks: Int) : DrinkSyncStep

    /** Sending the [drinks] records newer than what the server holds. */
    data class Uploading(val drinks: Int) : DrinkSyncStep

    /** Over: [newDrinks] were stored for the first time (0 when the server held them all). */
    data class Done(val newDrinks: Int) : DrinkSyncStep

    data class Failed(val reason: DrinkSyncFailure) : DrinkSyncStep
}

/** Why a sync failed, in the terms the page says it. */
sealed interface DrinkSyncFailure {
    /** Bluetooth could not be used, or the fountain did not give its history ([FountainLinkFailure]). */
    data class Link(val reason: FountainLinkFailure) : DrinkSyncFailure

    /** The server holds no pairing secret for this fountain (409 `secret_unknown`); [message] is its own. */
    data class SecretUnknown(val message: String?) : DrinkSyncFailure

    /** The secret the server holds is not 8 bytes (409 `secret_invalid`, or a malformed one). */
    data class SecretInvalid(val message: String?) : DrinkSyncFailure

    /** The server says this accessory's history cannot be synced. */
    data object Unsupported : DrinkSyncFailure

    /** Reaching the server failed: before the sync, or when uploading what was read. */
    data class Server(val error: DomainError) : DrinkSyncFailure
}

/**
 * Syncs one fountain's drink history (issue #104): checks the phone's Bluetooth, asks petkit-local
 * for the fountain's address and secret, runs PetKit's session with the fountain, uploads the
 * records newer than what the server already holds, re-reads the timeline of the days they fall
 * on so History shows them, and records the success on this phone.
 *
 * The caller has checked the Bluetooth permissions ([BluetoothAccess]). The secret lives only
 * for the session and is wiped after it, whatever happened.
 */
class SyncDrinksUseCase @Inject constructor(
    private val repository: DrinkSyncRepository,
    private val bluetooth: FountainBluetooth,
    private val timeline: TimelineRepository,
    private val clock: Clock,
) {
    operator fun invoke(accessoryId: Long): Flow<DrinkSyncStep> = channelFlow {
        send(DrinkSyncStep.Searching)
        send(sync(accessoryId) { send(it) })
    }

    private suspend fun sync(accessoryId: Long, report: suspend (DrinkSyncStep) -> Unit): DrinkSyncStep {
        val ready = prepare(accessoryId)
        if (ready !is Preparation.Ready) return (ready as Preparation.Refused).step
        val read = try {
            bluetooth.readHistory(ready.info.mac, ready.secret) { progress -> report(progress.toStep()) }
        } finally {
            ready.secret.wipe()
        }
        return when (read) {
            is FountainReadResult.Failure -> failed(read.reason)
            is FountainReadResult.Success -> upload(ready.info, read.records, report)
        }
    }

    private sealed interface Preparation {
        class Ready(val info: BleSyncInfo, val secret: FountainSecret) : Preparation

        class Refused(val step: DrinkSyncStep.Failed) : Preparation
    }

    /** The phone's Bluetooth checked, then the server asked for the fountain's address and secret. */
    private suspend fun prepare(accessoryId: Long): Preparation {
        val off = when (bluetooth.availability()) {
            BluetoothAvailability.Unavailable -> FountainLinkFailure.BluetoothUnavailable
            BluetoothAvailability.Off -> FountainLinkFailure.BluetoothOff
            BluetoothAvailability.Ready -> null
        }
        if (off != null) return Preparation.Refused(failed(off))
        val answer = repository.bleSync(accessoryId)
        val info = (answer as? Outcome.Success)?.value
        val secret = info?.secret
        return when {
            answer is Outcome.Failure -> Preparation.Refused(DrinkSyncStep.Failed(serverFailure(answer.error)))
            info?.syncSupported != true -> Preparation.Refused(DrinkSyncStep.Failed(DrinkSyncFailure.Unsupported))
            secret == null -> Preparation.Refused(DrinkSyncStep.Failed(DrinkSyncFailure.SecretInvalid(null)))
            else -> Preparation.Ready(info, secret)
        }
    }

    private suspend fun upload(
        info: BleSyncInfo,
        records: List<DrinkRecord>,
        report: suspend (DrinkSyncStep) -> Unit,
    ): DrinkSyncStep {
        // Only what the server does not hold yet: the upload is idempotent, but smaller is kinder.
        val cursor = info.historyCursor
        val fresh = records.distinctBy { it.workTime }
            .filter { cursor == null || it.workTime + info.clockEpoch > cursor }
            .sortedBy { it.workTime }
        var stored = 0
        if (fresh.isNotEmpty()) {
            report(DrinkSyncStep.Uploading(fresh.size))
            for (chunk in fresh.chunked(DrinkSyncRepository.MAX_RECORDS_PER_UPLOAD)) {
                when (val result = repository.uploadDrinks(info.id, chunk)) {
                    is Outcome.Failure -> return DrinkSyncStep.Failed(DrinkSyncFailure.Server(result.error))
                    is Outcome.Success -> stored += result.value.stored
                }
            }
            refreshDays(fresh.map { Instant.ofEpochSecond(it.workTime + info.clockEpoch) })
        }
        repository.recordSync(info.id, clock.instant())
        return DrinkSyncStep.Done(stored)
    }

    /**
     * The local days the new drinks fall on, the newest [MAX_DAYS_REFRESHED] of them (a first sync
     * can bring weeks; History reads older days when it shows them). A failure here costs nothing
     * but freshness: the cards are on the server, and the next refresh brings them.
     */
    private suspend fun refreshDays(times: List<Instant>) {
        val days: List<LocalDate> = times.map { it.atZone(clock.zone).toLocalDate() }
            .distinct().sortedDescending().take(MAX_DAYS_REFRESHED)
        days.forEach { timeline.refreshDay(it) }
    }

    private fun failed(reason: FountainLinkFailure): DrinkSyncStep.Failed =
        DrinkSyncStep.Failed(DrinkSyncFailure.Link(reason))

    private fun serverFailure(error: DomainError): DrinkSyncFailure {
        val server = error as? DomainError.Server
        return when (server?.code) {
            SECRET_UNKNOWN -> DrinkSyncFailure.SecretUnknown(server.message?.takeIf { it.isNotBlank() })
            SECRET_INVALID -> DrinkSyncFailure.SecretInvalid(server.message?.takeIf { it.isNotBlank() })
            else -> DrinkSyncFailure.Server(error)
        }
    }

    private fun FountainLinkProgress.toStep(): DrinkSyncStep = when (this) {
        FountainLinkProgress.Searching -> DrinkSyncStep.Searching
        FountainLinkProgress.Connected -> DrinkSyncStep.Connected
        is FountainLinkProgress.Reading -> DrinkSyncStep.Reading(drinks)
    }

    companion object {
        const val SECRET_UNKNOWN = "secret_unknown"
        const val SECRET_INVALID = "secret_invalid"

        /** How many of the days the new drinks fall on are re-read at once. */
        const val MAX_DAYS_REFRESHED = 7
    }
}

/** When [accessoryId]'s drinks were last synced on this phone. */
class ObserveLastDrinkSyncUseCase @Inject constructor(private val repository: DrinkSyncRepository) {
    operator fun invoke(accessoryId: Long): Flow<Instant?> = repository.observeLastSync(accessoryId)
}
