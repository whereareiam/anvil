package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageCollector
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Collects the classes a segment takes from its base chain: every class of the bases except their adapters, the
 * implementations each base declares in `META-INF/services`, and the classes the segment [replaces][replaced], each
 * with its nested classes. An adapter serves only its own release, and the extending segment declares its own, so the
 * classes it brings are exactly the ones that can run on the extending segment's releases.
 *
 * A replaced class must be a class of the base chain, and no class the segment keeps may still use it: the segment
 * then replaces that class too, or brings the classes that use it.
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
	 * Path of the extending segment, which failures name.
	 */
	@get:Input
	abstract val segmentPath: Property<String>

	/**
	 * Binary names of the base classes the segment replaces with classes of its own.
	 */
	@get:Input
	abstract val replaced: SetProperty<String>

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
			.map(::internalName)
			.toSet()
		val classes = roots
			.flatMap { root ->
				root.walkTopDown()
					.filter { it.isFile && it.name.endsWith(".class") }
					.map { file -> file.relativeTo(root).invariantSeparatorsPath.removeSuffix(".class") to file }
			}
			.filter { (name) -> topLevel(name) !in adapters }
			.toMap()

		val replaced = replaced.get().map(::internalName).toSet()
		val topLevelClasses = classes.keys.map(::topLevel).toSet()
		replaced.firstOrNull { it !in topLevelClasses }?.let {
			throw GradleException(
				"Segment ${segmentPath.get()} replaces ${binaryName(it)}, which is not a class of its base chain"
			)
		}

		val kept = classes.filterKeys { topLevel(it) !in replaced }
		requireUnused(kept, replaced)

		val target = destination.get().asFile
		target.deleteRecursively()
		kept.forEach { (name, file) -> file.copyTo(File(target, "$name.class"), overwrite = true) }
	}

	/**
	 * Fails when a kept class still uses a replaced one, which the extending segment would not carry.
	 */
	private fun requireUnused(kept: Map<String, File>, replaced: Set<String>) {
		if (replaced.isEmpty()) return

		for ((name, file) in kept) {
			val references = LinkageCollector().apply { add(file.readBytes(), file.path) }.referencedClasses
			val used = references.firstOrNull { topLevel(it) in replaced } ?: continue
			throw GradleException(
				"Segment ${segmentPath.get()} replaces ${binaryName(topLevel(used))}, which its base class "
					+ "${binaryName(name)} still uses; replace ${binaryName(topLevel(name))} too"
			)
		}
	}

	private fun internalName(binaryName: String): String = binaryName.replace('.', '/')

	private fun binaryName(internalName: String): String = internalName.replace('/', '.')

	private fun topLevel(internalName: String): String = internalName.substringBefore('$')
}
