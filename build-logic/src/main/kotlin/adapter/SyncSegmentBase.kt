package me.whereareiam.anvil.buildlogic.adapter

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Collects the classes a segment takes from its base chain: every class of the bases except their adapters, the
 * implementations each base declares in `META-INF/services`, with their nested classes. An adapter serves only its
 * own release, and the extending segment declares its own, so the classes it brings are exactly the ones that can
 * run on the extending segment's releases.
 */
@CacheableTask
abstract class SyncSegmentBase : DefaultTask() {
	/**
	 * Class and resource directories of the base chain.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val base: ConfigurableFileCollection

	/**
	 * Directory receiving the shared classes.
	 */
	@get:OutputDirectory
	abstract val destination: DirectoryProperty

	@TaskAction
	fun sync() {
		val roots = base.files.filter(File::isDirectory)
		val adapters = roots
			.flatMap { root -> File(root, "META-INF/services").listFiles().orEmpty().toList() }
			.flatMap { it.readLines() }
			.map { it.substringBefore('#').trim() }
			.filter(String::isNotEmpty)
			.map { it.replace('.', '/') }
			.toSet()

		val target = destination.get().asFile
		target.deleteRecursively()
		for (root in roots)
			root.walkTopDown()
				.filter { it.isFile && it.name.endsWith(".class") }
				.forEach { file ->
					val name = file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class")
					if (name.substringBefore('$') !in adapters) file.copyTo(File(target, "$name.class"), overwrite = true)
				}
	}
}
