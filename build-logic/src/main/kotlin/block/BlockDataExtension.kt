package me.whereareiam.anvil.buildlogic.block

import me.whereareiam.anvil.buildlogic.module.catalog
import me.whereareiam.anvil.buildlogic.module.library
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject

/**
 * The `blockData` extension of the `module-block-data` convention: it makes a module generate its block data from the
 * block data the physics harness's `generateVanillaData` read from each release's vanilla client.
 *
 * <pre>
 * blockData.generate(
 *     generator = "me.whereareiam.anvil.protocol.java.block.BlockDataGenerator",
 *     packagePath = "me/whereareiam/anvil/protocol/java/block",
 *     vanilla = layout.projectDirectory.dir("vanilla"),
 * )
 * </pre>
 */
abstract class BlockDataExtension @Inject constructor(private val project: Project) {
	/**
	 * Wires the generation: the `generator` source set with Jackson, whose [generator] class writes the main
	 * resources below [packagePath] from every `<protocol>.blocks` of [vanilla] through the
	 * [BlockDataGeneratorArguments] request, `generateBlockData`, and `checkBlockDataReleases`, which `check` runs.
	 * Tests compile against the generator too.
	 */
	fun generate(generator: String, packagePath: String, vanilla: Directory) = with(project) {
		// The generator is Java beside the data's own model, so the shipped data and the codec that writes and reads
		// it exist once. It compiles against the main classes and writes the main resources; tests use it too.
		val sourceSets = extensions.getByType<SourceSetContainer>()
		val main = sourceSets.getByName("main")
		val generatorSet = sourceSets.create("generator") {
			compileClasspath += files(main.java.classesDirectory)
			runtimeClasspath += files(main.java.classesDirectory)
		}
		configurations.getByName("generatorImplementation")
			.extendsFrom(configurations.getByName("api"), configurations.getByName("implementation"))
		configurations.getByName("generatorCompileOnly").extendsFrom(configurations.getByName("compileOnly"))
		dependencies.add("generatorImplementation", catalog.library("jackson-databind"))

		val blocks = vanilla.asFileTree.matching { include("*$SUFFIX") }
		val arguments = objects.newInstance<BlockDataGeneratorArguments>().apply {
			this.blocks.from(blocks)
			destination.set(layout.buildDirectory.dir("generated/block-data"))
			this.packagePath.set(packagePath)
		}
		val generate = tasks.register<JavaExec>("generateBlockData") {
			description = "Generates the block data resources from the block data read from each release's vanilla client."
			classpath = generatorSet.runtimeClasspath
			mainClass.set(generator)
			argumentProviders.add(arguments)
		}
		main.resources.srcDir(generate.map { arguments.destination.get() })

		val test = sourceSets.getByName("test")
		test.compileClasspath += generatorSet.output
		test.runtimeClasspath += generatorSet.output
		configurations.getByName("testImplementation").extendsFrom(configurations.getByName("generatorImplementation"))

		val check = tasks.register<CheckBlockDataReleases>("checkBlockDataReleases") {
			description = "Checks that every protocol of every protocol library release has block data read from its vanilla client."
			group = LifecycleBasePlugin.VERIFICATION_GROUP
			this.blocks.from(blocks)
			libraryReleases.from(layout.settingsDirectory.file(LIBRARY_RELEASES))
			marker.set(layout.buildDirectory.file("checks/block-data-releases"))
		}
		tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(check) }
	}

	internal companion object {
		const val SUFFIX = ".blocks"
		const val LIBRARY_RELEASES = "anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml"
	}
}
