package org.petkitlocal.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * The external libraries this project's own modules declare on [configurationName] (e.g.
 * `releaseRuntimeClasspath`), as `group:module`: the first-level external dependencies of this
 * module and of every project module the configuration pulls in, never what those libraries bring
 * along themselves. Platforms (BOMs) are left out: they fix versions and hold no code.
 *
 * Read from the configuration's resolution result, lazily, so it is what Gradle resolved (the
 * version a BOM chose, a project dependency's own variant) and never a hand-kept list.
 */
fun Project.directExternalDependencies(configurationName: String): Provider<Set<String>> =
    provider { configurations.getByName(configurationName) }
        .flatMap { it.incoming.resolutionResult.rootComponent }
        .map(::directExternalDependencies)

/** See [directExternalDependencies]: walks the project components, stops at the first external one. */
fun directExternalDependencies(root: ResolvedComponentResult): Set<String> {
    val found = sortedSetOf<String>()
    val seen = mutableSetOf<ComponentIdentifier>()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!seen.add(component.id)) continue
        for (dependency in component.dependencies) {
            if (dependency !is ResolvedDependencyResult || dependency.isConstraint) continue
            val selected = dependency.selected
            when (val id = selected.id) {
                is ProjectComponentIdentifier -> queue += selected
                is ModuleComponentIdentifier -> if (!dependency.isPlatform()) found += "${id.group}:${id.module}"
            }
        }
    }
    return found
}

private val PLATFORM_CATEGORIES = setOf("platform", "enforced-platform")

private fun ResolvedDependencyResult.isPlatform(): Boolean {
    val attributes = resolvedVariant.attributes
    val category = attributes.keySet().firstOrNull { it.name == "org.gradle.category" } ?: return false
    return attributes.getAttribute(category).toString() in PLATFORM_CATEGORIES
}

/** Writes [dependencies], one per line, sorted: what a test compares the shipped licences list with. */
abstract class DirectDependenciesReport : DefaultTask() {
    @get:Input
    abstract val dependencies: SetProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun write() {
        output.get().asFile.writeText(dependencies.get().sorted().joinToString("\n", postfix = "\n"))
    }
}
