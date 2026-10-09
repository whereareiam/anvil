package me.whereareiam.anvil.buildlogic.jvm

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Verifies that the classes of one compilation run on a Java release: no class file is newer than the release's
 * class file version, which is 44 plus the feature release (55 for Java 11).
 *
 * `compileForInServer` checks each compilation meant for the in-server release, so a build file that overrides the
 * compilation, or a compiler that ignores `--release`, cannot ship classes an old server rejects. It checks compiled
 * classes only; the platform provider convention checks the agent JAR a provider installs against the Java its
 * version data declares.
 */
@CacheableTask
abstract class CheckClassRelease : DefaultTask() {
	/**
	 * Class directories to check.
	 */
	@get:Classpath
	abstract val classes: ConfigurableFileCollection

	/**
	 * The Java feature release the classes must run on.
	 */
	@get:Input
	abstract val release: Property<Int>

	/**
	 * Summary of the checked classes.
	 */
	@get:OutputFile
	abstract val report: RegularFileProperty

	@TaskAction
	fun check() {
		val maximum = ClassFileVersion.newestMajor(release.get())
		val files = classes.files.filter(File::isDirectory)
			.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.map { root to it }.toList() }
		val newer = files.mapNotNull { (root, file) ->
			val major = file.inputStream().buffered().use { ClassFileVersion.major(it, file.path) }
			if (major <= maximum) null
			else "${file.relativeTo(root).invariantSeparatorsPath} (class file version $major, Java ${ClassFileVersion.java(major)})"
		}.sorted()

		report.get().asFile.writeText("${files.size} classes checked against Java ${release.get()} (class file version $maximum)\n"
			+ newer.joinToString("") { "$it\n" })
		if (newer.isNotEmpty())
			throw GradleException("Classes must run on Java ${release.get()} (class file version $maximum or older), but these are newer:\n"
				+ newer.joinToString("\n") { "  $it" })
	}
}
