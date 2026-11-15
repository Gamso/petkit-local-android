package org.petkitlocal.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.plugins.PluginManager
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("No library '$alias' in libs.versions.toml") }

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalArgumentException("No version '$alias' in libs.versions.toml") }
        .requiredVersion

internal fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).orElseThrow { IllegalArgumentException("No plugin '$alias' in libs.versions.toml") }
        .get().pluginId

internal fun PluginManager.apply(catalog: VersionCatalog, vararg aliases: String) {
    aliases.forEach { apply(catalog.pluginId(it)) }
}

/** `:core:designsystem` -> `org.petkitlocal.core.designsystem`. */
internal val Project.moduleNamespace: String
    get() = "org.petkitlocal." + path.removePrefix(":").replace(':', '.').replace('-', '_')
