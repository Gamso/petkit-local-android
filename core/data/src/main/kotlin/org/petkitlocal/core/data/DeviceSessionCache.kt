package org.petkitlocal.core.data

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.petkitlocal.core.database.PetkitDatabase
import org.petkitlocal.core.domain.auth.SessionScopedCache

/** The cached devices, pets and timeline: the whole Room database, cleared with the session. */
class DeviceSessionCache @Inject constructor(private val database: PetkitDatabase) : SessionScopedCache {
    override suspend fun clear() = withContext(Dispatchers.IO) {
        // A blocking call that Room refuses on the main thread.
        database.clearAllTables()
    }
}
