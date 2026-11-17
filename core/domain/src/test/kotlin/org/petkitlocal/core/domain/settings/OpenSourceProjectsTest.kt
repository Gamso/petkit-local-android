package org.petkitlocal.core.domain.settings

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenSourceProjectsTest {
    private val apache = OpenSourceLicence("Apache License 2.0", "text")

    private fun component(id: String, name: String, repository: String?, licence: OpenSourceLicence = apache) =
        OpenSourceComponent(id, name, "1.0", repository, listOf(licence))

    @Test
    fun `the artifacts of one GitHub repository form one project`() {
        val projects = OpenSourceProjects.group(
            listOf(
                component("io.coil-kt.coil3:coil-compose", "coil-compose", "https://github.com/coil-kt/coil"),
                component(
                    "io.coil-kt.coil3:coil-network-okhttp",
                    "coil-network-okhttp",
                    "https://github.com/coil-kt/coil/",
                ),
            ),
        )
        assertEquals(listOf("coil"), projects.map { it.name })
        assertEquals(2, projects.single().components.size)
    }

    @Test
    fun `every AndroidX and Compose artifact is one project, whatever its address`() {
        val projects = OpenSourceProjects.group(
            listOf(
                component(
                    "androidx.core:core-ktx",
                    "Core",
                    "https://cs.android.com/androidx/platform/frameworks/support",
                ),
                component("androidx.media3:media3-common", "Media3 common", "https://github.com/androidx/media"),
                component("x:y", "Compose thing", "https://developer.android.com/jetpack"),
            ),
        )
        assertEquals(listOf(OpenSourceProjects.ANDROIDX_NAME), projects.map { it.name })
        assertEquals(listOf("Compose thing", "Core", "Media3 common"), projects.single().components.map { it.name })
    }

    @Test
    fun `a component without a repository is a project on its own`() {
        val projects = OpenSourceProjects.group(listOf(component("x:one", "One", null), component("x:two", "Two", "")))
        assertEquals(listOf("One", "Two"), projects.map { it.name })
    }

    @Test
    fun `without a common beginning the shortest name is kept`() {
        val projects = OpenSourceProjects.group(
            listOf(
                component("s:retrofit", "Retrofit", "https://github.com/square/retrofit/"),
                component("s:converter", "Converter: kotlinx.serialization", "https://github.com/square/retrofit"),
            ),
        )
        assertEquals(listOf("Retrofit"), projects.map { it.name })
    }

    @Test
    fun `a project's licence names have no repetition`() {
        val mit = OpenSourceLicence("MIT License", null)
        val project = OpenSourceProjects.group(
            listOf(
                component("a:one", "one-a", "https://example.org/p", apache),
                component("a:two", "one-b", "https://example.org/p", apache),
                component("a:three", "one-c", "https://example.org/p", mit),
            ),
        ).single()
        assertEquals(listOf("Apache License 2.0", "MIT License"), project.licenceNames)
    }

    @Test
    fun `projects come sorted by name, whatever the case`() = runTest {
        val repository = object : OpenSourceLicencesRepository {
            override suspend fun components() = listOf(
                component("b:b", "okhttp", "https://github.com/square/okhttp"),
                component("a:a", "AboutLibraries", "https://github.com/mikepenz/AboutLibraries"),
                component("c:c", "Kotlin", "https://github.com/JetBrains/kotlin"),
            )

            override suspend fun appLicence() = OpenSourceLicence("MIT License", "text")
        }

        val names = GetOpenSourceProjectsUseCase(repository)().map { it.name }

        assertEquals(listOf("AboutLibraries", "Kotlin", "okhttp"), names)
    }
}
