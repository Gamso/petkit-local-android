package org.petkitlocal.android.licences

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.petkitlocal.android.R
import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.petkitlocal.core.domain.settings.OpenSourceLicence
import org.petkitlocal.core.domain.settings.OpenSourceLicencesRepository

/**
 * The licences list the AboutLibraries Gradle plugin generated from the build's direct release
 * dependencies (app/build.gradle.kts), and the app's own LICENSE, both read from the APK once and
 * kept: they cannot change while the app runs.
 */
@Singleton
class AboutLibrariesRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    OpenSourceLicencesRepository {
    private val mutex = Mutex()
    private var cached: List<OpenSourceComponent>? = null

    override suspend fun components(): List<OpenSourceComponent> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { openSourceComponents(raw(R.raw.aboutlibraries)) }.also { cached = it }
    }

    override suspend fun appLicence(): OpenSourceLicence =
        OpenSourceLicence(name = APP_LICENCE, text = withContext(Dispatchers.IO) { raw(R.raw.license) })

    private fun raw(id: Int): String = context.resources.openRawResource(id).bufferedReader().use { it.readText() }

    private companion object {
        /** What the repository's LICENSE is (and app/src/main/res/raw/license.txt, its copy). */
        const val APP_LICENCE = "MIT License"
    }
}

/** The components of an `aboutlibraries.json`, as the domain names them. */
internal fun openSourceComponents(json: String): List<OpenSourceComponent> =
    Libs.Builder().withJson(json).build().libraries.map { library ->
        OpenSourceComponent(
            id = library.uniqueId,
            name = library.name,
            version = library.artifactVersion,
            repository = library.scm?.url ?: library.website,
            licences = library.licenses.map { licence ->
                OpenSourceLicence(name = licence.name, text = licence.licenseContent?.takeIf { it.isNotBlank() })
            },
        )
    }
