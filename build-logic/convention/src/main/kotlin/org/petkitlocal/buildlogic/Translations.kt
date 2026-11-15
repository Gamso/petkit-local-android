package org.petkitlocal.buildlogic

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/**
 * Every user-facing string in English AND French (SPEC.md §7), as a build failure: each
 * translatable `<string>`, `<plurals>` and `<string-array>` of a `values/` folder needs its
 * counterpart in the sibling `values-fr/`, and the other way round (a French string left behind
 * by a removed English one is caught too). French plurals need at least `one` and `other`.
 *
 * Android lint's MissingTranslation says much the same, but only for a module that already has a
 * `values-fr/` folder: a module whose first strings arrive without one passes lint. This does not.
 */
internal fun Project.configureTranslationCheck() {
    val task = tasks.register("checkTranslations", TranslationsTask::class.java) {
        group = "verification"
        description = "Fails if a user-facing string has no French translation (SPEC.md §7)."
        resources.from(fileTree("src") { include("*/res/values/*.xml", "*/res/values-fr/*.xml") })
        report.set(layout.buildDirectory.file("reports/translations.txt"))
    }
    tasks.named("check") { dependsOn(task) }
}

@CacheableTask
abstract class TranslationsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        // One source set's res/ folder at a time: main, debug... each translated on its own.
        val problems = resources.files.map { it.parentFile.parentFile }.distinct().sorted().flatMap(::checkResDir)
        val file = report.get().asFile
        if (problems.isNotEmpty()) {
            file.writeText("FAILED\n" + problems.joinToString("\n") + "\n")
            throw GradleException(
                "Missing or stale French translations (SPEC.md §7):\n" + problems.joinToString("\n") { "  $it" },
            )
        }
        file.writeText("OK\n")
    }

    private fun checkResDir(res: File): List<String> {
        val english = read(File(res, "values"))
        val french = read(File(res, "values-fr"))
        val where = res.relativeToOrSelf(res.parentFile.parentFile.parentFile).path.replace('\\', '/')
        val missing = (english.keys - french.keys).sorted().map { "$where: no French for ${english.getValue(it)} $it" }
        val stale = (french.keys - english.keys).sorted().map { "$where: French ${french.getValue(it)} $it has no English" }
        val plurals = french.filterValues { it.startsWith("plurals") }.keys.sorted().mapNotNull { name ->
            val quantities = french.getValue(name).substringAfter(':').split(',').toSet()
            val absent = REQUIRED_FRENCH_QUANTITIES - quantities
            if (absent.isEmpty()) null else "$where: French plurals $name lacks ${absent.sorted().joinToString()}"
        }
        return missing + stale + plurals
    }

    /** Translatable resources by name; the value is the kind, plus a plural's quantities. */
    private fun read(folder: File): Map<String, String> {
        val files = folder.listFiles { file -> file.extension == "xml" }.orEmpty()
        return files.flatMap { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            if (root.getAttribute("translatable") == "false") return@flatMap emptyList()
            (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
                .filter { it.tagName in KINDS && it.getAttribute("translatable") != "false" }
                .map { element -> element.getAttribute("name") to describe(element) }
        }.toMap()
    }

    private fun describe(element: Element): String {
        if (element.tagName != "plurals") return element.tagName
        val items = element.getElementsByTagName("item")
        return "plurals:" + (0 until items.length).joinToString(",") { (items.item(it) as Element).getAttribute("quantity") }
    }

    private companion object {
        val KINDS = setOf("string", "plurals", "string-array")
        val REQUIRED_FRENCH_QUANTITIES = setOf("one", "other")
    }
}
