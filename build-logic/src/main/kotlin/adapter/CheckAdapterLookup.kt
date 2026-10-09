package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageCollector
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Verifies that a library side's own classes, its capability wiring, obtain release-specific adapters only through
 * the worker's adapter lookup (`PlayerBindingContext.adapter`): none may reference `java.util.ServiceLoader`. A
 * service lookup of its own would bypass the worker's segment selection and linkage self-check, and its failures
 * would not name the capability they make unavailable.
 */
@CacheableTask
abstract class CheckAdapterLookup : DefaultTask() {
	/**
	 * The side's compiled main classes; a side without sources has none.
	 */
	@get:Classpath
	abstract val classes: ConfigurableFileCollection

	/**
	 * Summary of the checked classes.
	 */
	@get:OutputFile
	abstract val report: RegularFileProperty

	@TaskAction
	fun check() {
		val files = classes.files.filter(File::isDirectory)
			.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.map { root to it }.toList() }
		val offending = files.mapNotNull { (root, file) ->
			val collector = LinkageCollector().apply { add(file.readBytes(), file.path) }
			val lookups = collector.referencedClasses.filter { it == serviceLoader || it.startsWith("$serviceLoader$") }
			if (lookups.isEmpty()) null else file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
		}.sorted()

		report.get().asFile.writeText("${files.size} classes checked for service lookups\n" + offending.joinToString("") { "$it\n" })
		if (offending.isNotEmpty())
			throw GradleException("Library side classes must obtain adapters through PlayerBindingContext.adapter(Class), "
				+ "not java.util.ServiceLoader: " + offending.joinToString())
	}

	private companion object {
		const val serviceLoader = "java/util/ServiceLoader"
	}
}
