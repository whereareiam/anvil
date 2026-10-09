package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.adapter.linkage.CheckSegmentLinkage
import me.whereareiam.anvil.buildlogic.adapter.linkage.ClassIndex
import me.whereareiam.anvil.buildlogic.adapter.linkage.JdkClasses
import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageCollector
import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import java.io.File

/**
 * Writes the resources that describe a segment inside its JAR:
 *
 * - `META-INF/anvil/segment.properties` with `library`, `owner` (the side folder) and `since`, which the worker
 *   uses to select one segment per owner;
 * - `META-INF/anvil/segment/linkage.txt` with every class, field and method the segment links against outside
 *   the JDK, its own packages and its project dependencies' packages, which the worker verifies against the
 *   loaded release and [CheckSegmentLinkage] verifies against later releases.
 *
 * The JDK is the one of the project's toolchain and is an input, so the manifest does not depend on the JDK that
 * runs Gradle.
 */
@CacheableTask
abstract class WriteSegmentManifest : DefaultTask() {
	/**
	 * Registered library id.
	 */
	@get:Input
	abstract val library: Property<String>

	/**
	 * Side folder holding the segment.
	 */
	@get:Input
	abstract val owner: Property<String>

	/**
	 * Minecraft version from which the segment applies.
	 */
	@get:Input
	abstract val since: Property<String>

	/**
	 * The segment's compiled classes.
	 */
	@get:Classpath
	abstract val classes: ConfigurableFileCollection

	/**
	 * The segment's compile classpath, on which inherited members are traced to the class declaring them.
	 */
	@get:CompileClasspath
	abstract val classpath: ConfigurableFileCollection

	/**
	 * Compiled project dependencies, whose packages belong to the build rather than to the library.
	 */
	@get:CompileClasspath
	abstract val projectDependencies: ConfigurableFileCollection

	/**
	 * The JDK of the project's toolchain, whose classes the manifest leaves out.
	 */
	@get:Nested
	abstract val jdk: Property<JavaLauncher>

	/**
	 * Resource root receiving `META-INF/anvil`.
	 */
	@get:OutputDirectory
	abstract val destination: DirectoryProperty

	@TaskAction
	fun write() {
		val segment = SegmentName(MinecraftVersion.parse(since.get()))
		val collector = LinkageCollector()
		classes.files.filter(File::isDirectory)
			.flatMap { root -> root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.toList() }
			.forEach { collector.add(it.readBytes(), it.path) }

		val references = JdkClasses.open(jdk.get().metadata.installationPath.asFile).use { jdkClasses ->
			val buildPackages = (collector.classNames + ClassIndex.of(projectDependencies.files, jdkClasses).classNames).map(::packageOf).toSet()
			collector.externalReferences(ClassIndex.of(classpath.files, jdkClasses))
				.filterNot { packageOf(it.owner) in buildPackages || jdkClasses.contains(it.owner) }
		}

		val root = destination.get().asFile
		root.deleteRecursively()
		File(root, "META-INF/anvil/segment").mkdirs()
		File(root, "META-INF/anvil/segment.properties").writeText(
			"library=${library.get()}\nowner=${owner.get()}\nsince=${segment.since}\n"
		)
		File(root, "META-INF/anvil/segment/linkage.txt").writeText(references.joinToString("") { it.line + "\n" })
	}

	private fun packageOf(className: String): String = className.substringBeforeLast('/', "")
}
