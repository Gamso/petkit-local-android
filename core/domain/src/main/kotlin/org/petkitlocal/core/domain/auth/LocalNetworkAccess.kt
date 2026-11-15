package org.petkitlocal.core.domain.auth

/**
 * Android 17 makes reaching the local network a runtime permission (`ACCESS_LOCAL_NETWORK`).
 * Without it nothing fails loudly: the packets to a private address are dropped and every call
 * times out. So a server on the LAN ([ServerAddresses.isPrivateLanHost]) is only probed once the
 * user has granted it.
 */
fun interface LocalNetworkAccess {
    /** True when the app must still ask before it can reach a LAN host: Android 17+, not granted. */
    fun permissionNeeded(): Boolean
}
