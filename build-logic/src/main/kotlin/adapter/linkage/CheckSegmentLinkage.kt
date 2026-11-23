package me.whereareiam.anvil.buildlogic.adapter.linkage

import me.whereareiam.anvil.buildlogic.library.ReleaseClasspath
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Verifies that a segment links against every library release it serves, as workers load them.
 *
 * The segment compiles against one release's module; the references its linkage manifest lists into that module's
 * dependency graph are library references. Workers load the locked closure of release data instead, so each library
 * reference must link on the closure of the compiled release and of each later release: the class or member exists,
 * inherited members resolve through superclasses and interfaces, classes stay classes or interfaces, members stay
 * static or instance, and access is not narrower than on the compiled release. References outside the library do
 * not change between releases.
 *
 * The check runs the library's own segment linkage tool, the code the worker checks the manifest with at run time, so
 * the build and the worker apply one rule.
 */
@CacheableTask
abstract class CheckSegmentLinkage : DefaultTask() {
	/**
	 * The segment's `linkage.txt` manifest.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val linkage: RegularFileProperty

	/**
	 * The compiled release's module with its runtime dependencies, which tells library references apart.
	 */
	@get:Classpath
	abstract val library: ConfigurableFileCollection

	/**
	 * The locked closure of the release the segment compiles against.
	 */
	@get:Nested
	abstract val release: Property<ReleaseClasspath>

	/**
	 * The locked closures of the later releases the segment serves until the next segment starts.
	 */
	@get:Nested
	abstract val laterReleases: ListProperty<ReleaseClasspath>

	/**
	 * The library's common project with its runtime dependencies, which provides the segment linkage tool.
	 */
	@get:Classpath
	abstract val linkageTool: ConfigurableFileCollection

	/**
	 * The JDK of the project's toolchain, whose classes resolve members inherited from JDK supertypes.
	 */
	@get:Nested
	abstract val jdk: Property<JavaLauncher>

	/**
	 * Summary of the checked releases and failures.
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
		val tool = SegmentLinkageTool(workers, linkageTool, temporaryDir)
		val options = listOf(
			"--linkage", linkage.get().asFile.path,
			"--library", SegmentLinkageTool.paths(library.files),
			"--jdk", jdk.get().metadata.installationPath.asFile.path,
		)
		val libraryReferences = tool.run("references", *options.toTypedArray())
		val compiled = release.get()
		fun failures(release: ReleaseClasspath): List<String> =
			tool.run("check", *options.toTypedArray(), "--classpath", SegmentLinkageTool.paths(release.files))
				.map { "${release.version.get()} (Minecraft ${release.minecraft.get()}): $it" }

		val ownFailures = failures(compiled)
		val laterFailures = laterReleases.get().flatMap(::failures)
		writeReport(libraryReferences.size, ownFailures + laterFailures)

		if (ownFailures.isNotEmpty())
			throw GradleException("Segment does not link against the closure that release data locks for its own release "
				+ "${compiled.version.get()}; pin the release again or fix its [[release.artifact]] tables:\n"
				+ (ownFailures + laterFailures).joinToString("\n") { "  $it" })
		if (laterFailures.isNotEmpty())
			throw GradleException("Segment does not link against every release it serves; add a segment for the first failing release:\n"
				+ laterFailures.joinToString("\n") { "  $it" })
	}

	private fun writeReport(libraryReferences: Int, failures: List<String>) {
		val checked = laterReleases.get().joinToString { it.version.get() }.ifEmpty { "none" }
		report.get().asFile.writeText(buildString {
			appendLine("compiled against ${release.get().version.get()}; $libraryReferences library references")
			appendLine("later releases: $checked")
			failures.forEach(::appendLine)
		})
	}
}
