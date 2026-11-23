package me.whereareiam.anvil.buildlogic.adapter.linkage

import me.whereareiam.anvil.buildlogic.library.LibraryRegistry
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.kotlin.dsl.named
import org.gradle.workers.WorkerExecutor
import java.io.File

/**
 * Runs the segment linkage of a library from its own classes: the build writes a segment's linkage manifest, checks
 * it against each release the segment serves and finds which classes a class file uses with the same code that the
 * library's worker checks the manifest with at run time.
 *
 * Each [run] executes one command of the tool through the Worker API, in a class loader of [classpath] alone, and
 * returns the lines it printed. See the library's `SegmentLinkageTool` for the commands.
 *
 * @param workers the task's worker executor
 * @param classpath the library's common project with its runtime dependencies
 * @param answers directory receiving the answers, such as the task's temporary directory
 */
internal class SegmentLinkageTool(
	private val workers: WorkerExecutor,
	private val classpath: FileCollection,
	private val answers: File,
) {
	private var runs = 0

	/**
	 * Runs one command with its options and waits for its answer.
	 *
	 * @return the lines the tool printed, without empty ones
	 */
	fun run(vararg arguments: String): List<String> {
		val answer = File(answers, "segment-linkage-${++runs}.txt")
		workers.classLoaderIsolation { classpath.from(this@SegmentLinkageTool.classpath) }.submit(SegmentLinkageAction::class.java) {
			this.arguments.set(arguments.toList())
			this.answer.set(answer)
		}
		workers.await()
		return answer.readLines().filter(String::isNotEmpty)
	}

	companion object {
		/**
		 * Joins files into one path list option.
		 */
		fun paths(files: Iterable<File>): String = files.joinToString(File.pathSeparator) { it.path }
	}
}

/**
 * Creates the configuration that resolves the segment linkage tool of [library]: its common project with its runtime
 * dependencies, which build tasks run the tool from. The configuration is not one the project compiles against or
 * packages, so the architecture rules of production configurations do not apply to it.
 */
fun Project.segmentLinkageTool(library: String): NamedDomainObjectProvider<ResolvableConfiguration> {
	val tool = dependencies.project(mapOf("path" to LibraryRegistry.commonProjectPath(library)))
	val scope = configurations.dependencyScope("segmentLinkageTool") {
		dependencies.add(tool)
	}
	return configurations.resolvable("segmentLinkageToolClasspath") {
		extendsFrom(scope.get())
		attributes {
			attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
			attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
			attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
			attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
		}
	}
}
