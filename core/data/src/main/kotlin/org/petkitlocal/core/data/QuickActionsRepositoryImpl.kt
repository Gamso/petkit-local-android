package org.petkitlocal.core.data

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.datastore.QuickActionsStore
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.repository.QuickActionsRepository

/** The user's actions in view per device, in their own DataStore file on the phone. */
class QuickActionsRepositoryImpl @Inject constructor(private val store: QuickActionsStore) : QuickActionsRepository {
    override fun observe(deviceId: Long): Flow<List<String>?> = store.pinned(deviceId)

    override suspend fun update(deviceId: Long, transform: (List<String>?) -> List<String>?): Boolean = try {
        store.update(deviceId, transform)
        true
    } catch (_: IOException) {
        // A full or unwritable disk: the choice stays as it was, and the editor shows it so.
        false
    }
}

/**
 * The same choices, kept across sessions on one server (owner decision): signing out, or a
 * session the server ended, keeps them; a sign-in to another server forgets them, since its
 * device ids are other devices.
 */
class QuickActionsServerCache @Inject constructor(private val store: QuickActionsStore) : ServerScopedCache {
    override suspend fun keepOnlyFor(serverBaseUrl: String) {
        try {
            store.keepOnlyFor(serverBaseUrl)
        } catch (_: IOException) {
            // Unwritable: the old choices stay, and only ever match a device id by chance.
        }
    }
}
