package org.petkitlocal.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The actions each device keeps in view on its collapsed action sheet, as the user chose them
 * (issue #40). Kept on the phone only, per device id, as action keys in order. They outlive the
 * session (signing out, a revoked or expired session) and are forgotten only when the user signs
 * in to another server, whose device ids mean other devices. A choice for a device the server
 * no longer lists is simply never read.
 */
interface QuickActionsRepository {
    /** The device's chosen keys, in order; null while the user has not chosen (the app does). */
    fun observe(deviceId: Long): Flow<List<String>?>

    /**
     * Replaces the device's choice with what [transform] makes of the stored one, in one step,
     * so two quick edits cannot undo each other. A null result forgets the choice (the app's
     * own ranking again). False when the phone could not write it: nothing changed then.
     */
    suspend fun update(deviceId: Long, transform: (List<String>?) -> List<String>?): Boolean
}
