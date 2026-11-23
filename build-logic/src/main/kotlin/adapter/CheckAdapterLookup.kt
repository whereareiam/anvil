package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.adapter.linkage.SegmentLinkageTool
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

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
	 * The library's common project with its runtime dependencies, whose segment linkage tool finds the classes a
	 * class file uses.
	 */
	@get:Classpath
	abstract val linkageTool: ConfigurableFileCollection

	/**
	 * Summary of the checked classes.
	 */
	@get:OutputFile
	abstract val report: RegularFileProperty

	/**
	 * Runs the linkage tool in a class loader of its own.
	 */
	@get:Inject
	abstract val workers: WorkerExecutor

	@TaskAction
	fun check() {
		val files = classes.files.filter(File::isDirectory)
			.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.map { root to it }.toList() }
		val lookups = if (files.isEmpty()) emptySet()
		else SegmentLinkageTool(workers, linkageTool, temporaryDir)
			.run("uses", "--of", serviceLoader, "--files", SegmentLinkageTool.paths(files.map { it.second }))
			.mapTo(mutableSetOf()) { it.substringBefore('\t') }
		val offending = files.filter { (_, file) -> file.path in lookups }
			.map { (root, file) -> file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.') }
			.sorted()

		report.get().asFile.writeText("${files.size} classes checked for service lookups\n" + offending.joinToString("") { "$it\n" })
		if (offending.isNotEmpty())
			throw GradleException("Library side classes must obtain adapters through PlayerBindingContext.adapter(Class), "
				+ "not java.util.ServiceLoader: " + offending.joinToString())
	}

	private companion object {
		const val serviceLoader = "java/util/ServiceLoader"
	}
}
