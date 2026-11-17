package org.petkitlocal.core.domain.settings

/** This build's version name, for the About section; the app module knows it. */
fun interface AppVersionProvider {
    fun versionName(): String
}
