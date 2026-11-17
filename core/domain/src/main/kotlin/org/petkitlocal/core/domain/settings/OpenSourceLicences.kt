package org.petkitlocal.core.domain.settings

import javax.inject.Inject

/** A licence: its name and, when the build knows it, its full text. */
data class OpenSourceLicence(val name: String, val text: String?)

/** One open-source artifact (or bundled asset) the app ships. */
data class OpenSourceComponent(
    val id: String,
    val name: String,
    val version: String?,
    /** Where its sources live (source repository, else website): the artifacts of a project share it. */
    val repository: String?,
    val licences: List<OpenSourceLicence>,
)

/** The components of one project, shown as one line: "coil" for coil-compose and coil-network-okhttp. */
data class OpenSourceProject(val name: String, val components: List<OpenSourceComponent>) {
    /** The names of its components' licences, without repetition. */
    val licenceNames: List<String> get() = components.flatMap { it.licences }.map { it.name }.distinct()
}

/**
 * The open-source components the app ships, with their licences (About), and the app's own licence.
 * The list is generated at build time from the build's direct release dependencies, never kept by
 * hand, and read from the APK: no network, no Google service.
 */
interface OpenSourceLicencesRepository {
    suspend fun components(): List<OpenSourceComponent>

    /** Petkit Local's own licence (the repository's LICENSE, shipped in the APK). */
    suspend fun appLicence(): OpenSourceLicence
}

/** The components grouped by project ([OpenSourceProjects.group]). */
class GetOpenSourceProjectsUseCase @Inject constructor(private val repository: OpenSourceLicencesRepository) {
    suspend operator fun invoke(): List<OpenSourceProject> = OpenSourceProjects.group(repository.components())
}

class GetAppLicenceUseCase @Inject constructor(private val repository: OpenSourceLicencesRepository) {
    suspend operator fun invoke(): OpenSourceLicence = repository.appLicence()
}

/** How the components come together as projects, one line each. */
object OpenSourceProjects {
    const val ANDROIDX_NAME = "AndroidX and Jetpack Compose"
    private const val ANDROIDX_PREFIX = "androidx."
    private const val ANDROID_HOST = "android.com"
    private const val ANDROIDX_KEY = "androidx"
    private const val GITHUB_HOST = "github.com"
    private const val GITHUB_PATH_SEGMENTS = 2
    private const val MIN_NAME_LENGTH = 3

    /**
     * Groups the components by project, which their source repository identifies (a GitHub
     * repository is one project, whatever the folder inside it). Every AndroidX and Compose
     * artifact is one project, whatever its address. A component without a repository is a project
     * on its own. Projects and their components are sorted by name, whatever the case.
     */
    fun group(components: List<OpenSourceComponent>): List<OpenSourceProject> = components
        .groupBy(::projectKey)
        .map { (key, members) ->
            OpenSourceProject(projectName(key, members), members.sortedBy { it.name.lowercase() })
        }
        .sortedBy { it.name.lowercase() }

    private fun projectKey(component: OpenSourceComponent): String {
        val repository = component.repository?.lowercase()?.takeIf { it.isNotBlank() } ?: return component.id
        if (component.id.startsWith(ANDROIDX_PREFIX) || ANDROID_HOST in repository) return ANDROIDX_KEY
        val address = repository.substringAfter("://").trimEnd('/').removeSuffix(".git")
        return if (address.startsWith(GITHUB_HOST)) {
            address.split('/').take(1 + GITHUB_PATH_SEGMENTS).joinToString("/")
        } else {
            address
        }
    }

    /**
     * The common beginning of its components' names ("coil" for "coil-compose" and
     * "coil-network-okhttp"), else the shortest name ("Retrofit" for it and its converter).
     */
    private fun projectName(key: String, members: List<OpenSourceComponent>): String {
        if (key == ANDROIDX_KEY) return ANDROIDX_NAME
        val names = members.map { it.name }
        if (names.size == 1) return names.single()
        val prefix = names.reduce { a, b -> a.commonPrefixWith(b) }.trimEnd('-', '_', '.', ' ')
        return if (prefix.length >= MIN_NAME_LENGTH) prefix else names.minBy { it.length }
    }
}
