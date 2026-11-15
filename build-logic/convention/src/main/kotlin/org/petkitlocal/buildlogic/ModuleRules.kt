package org.petkitlocal.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask

/**
 * The dependency rule of docs/ARCHITECTURE.md §2, as a build failure.
 *
 * It reads the RESOLVED compile classpath, not the declared dependencies, so a data-layer
 * module reaching a feature transitively (an `api` dependency of something it uses) is caught
 * as surely as a direct one: what is on the compile classpath can be imported.
 */
internal object ModuleRules {
    private val dataLayer = listOf(":core:data", ":core:network", ":core:database", ":core:datastore")

    /** Project paths [modulePath] must not see on its compile classpath. */
    fun forbiddenFor(modulePath: String): List<String> = when {
        modulePath.startsWith(":feature:") -> dataLayer
        // Pure Kotlin: models, interfaces, use cases. It sees no other module of the app.
        modulePath == ":core:domain" -> listOf(ANY_PROJECT)
        else -> emptyList()
    }

    const val ANY_PROJECT = "*"
}

internal fun Project.configureModuleRules() {
    val projectPath = path
    val forbidden = ModuleRules.forbiddenFor(projectPath)
    if (forbidden.isEmpty()) return

    val task = tasks.register("checkModuleRules", ModuleRulesTask::class.java) {
        group = "verification"
        description = "Fails if this module can see a module that docs/ARCHITECTURE.md §2 forbids."
        modulePath.set(projectPath)
        this.forbidden.set(forbidden)
        report.set(layout.buildDirectory.file("reports/module-rules.txt"))
    }
    // The compile classpath only exists once the Android or JVM plugin has created it.
    afterEvaluate {
        val classpath = listOf("debugCompileClasspath", "compileClasspath")
            .firstNotNullOfOrNull { configurations.findByName(it) }
            ?: throw GradleException("$projectPath: no compile classpath to check the module rules against")
        task.configure {
            projectDependencies.set(
                classpath.incoming.resolutionResult.rootComponent.map { root -> root.projectPaths() },
            )
        }
    }
    tasks.named("check") { dependsOn(task) }
}

private fun ResolvedComponentResult.projectPaths(): List<String> {
    val seen = linkedSetOf<String>()
    val queue = ArrayDeque(listOf(this))
    val visited = mutableSetOf<ResolvedComponentResult>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!visited.add(component)) continue
        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
            val selected = dependency.selected
            (selected.id as? ProjectComponentIdentifier)?.let { seen += it.projectPath }
            queue += selected
        }
    }
    return seen.toList()
}

@CacheableTask
abstract class ModuleRulesTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val forbidden: ListProperty<String>

    @get:Input
    abstract val projectDependencies: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val seen = projectDependencies.get()
        val rules = forbidden.get()
        val violations = seen.filter { dependency ->
            dependency != modulePath.get() && (ModuleRules.ANY_PROJECT in rules || dependency in rules)
        }
        val file = report.get().asFile
        if (violations.isNotEmpty()) {
            val message = "${modulePath.get()} must not depend on ${violations.joinToString()} " +
                "(docs/ARCHITECTURE.md §2: features see core:domain, core:designsystem and " +
                "core:ui, never the data layer; core:domain sees no other module)."
            file.writeText("FAILED\n$message\n")
            throw GradleException(message)
        }
        file.writeText("OK\n${modulePath.get()} sees: ${seen.joinToString().ifEmpty { "no module" }}\n")
    }
}
