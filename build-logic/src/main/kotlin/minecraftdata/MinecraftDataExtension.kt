package me.whereareiam.anvil.buildlogic.minecraftdata

import me.whereareiam.anvil.buildlogic.minecraftdata.pin.MinecraftDataPins
import me.whereareiam.anvil.buildlogic.minecraftdata.pin.MinecraftDataPinsFile
import me.whereareiam.anvil.buildlogic.minecraftdata.pin.PinMinecraftData
import me.whereareiam.anvil.buildlogic.module.catalog
import me.whereareiam.anvil.buildlogic.module.library
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject

/**
 * The `minecraftData` extension of the `module-minecraft-data` convention: it makes a module generate one
 * [MinecraftDataKind] from the minecraft-data files the kind's pins file pins.
 *
 * <pre>
 * minecraftData.generate(
 *     MinecraftDataKind("block data", "BlockData", "block-data.toml", listOf(MinecraftDataKind.File("blocks", "blocks"))),
 *     generator = "me.whereareiam.anvil.protocol.java.block.BlockDataGenerator",
 *     packagePath = "me/whereareiam/anvil/protocol/java/block",
 *     inputs = mapOf("facts" to layout.projectDirectory.file("block-facts.toml")),
 * )
 * </pre>
 */
abstract class MinecraftDataExtension @Inject constructor(private val project: Project) {
	/**
	 * Wires the generation of [kind]: the `minecraftData` configuration resolving the pinned files, the `generator`
	 * source set with Jackson, whose [generator] class writes the main resources below [packagePath] from the
	 * [MinecraftDataGeneratorArguments] request, `generate<Stem>`, `pin<Stem>`, and `check<Stem>Releases`, which
	 * `check` runs. Tests compile against the generator too.
	 *
	 * @param inputs the module's further input files by the request member that names them
	 */
	fun generate(
		kind: MinecraftDataKind,
		generator: String,
		packagePath: String,
		inputs: Map<String, RegularFile> = emptyMap(),
	) = with(project) {
		val pinsFile = layout.projectDirectory.file(kind.pinsFile)
		val pinned = MinecraftDataPinsFile(kind).read(pinsFile.asFile)
		val minecraftData = configurations.create("minecraftDataFiles") {
			description = "The minecraft-data files ${kind.pinsFile} pins."
			isCanBeConsumed = false
			isTransitive = false
		}
		dependencies.add(minecraftData.name, pinned.module) {
			pinned.files.forEach { file ->
				artifact {
					name = file.name
					classifier = file.directory
					extension = MinecraftDataPins.PinnedFile.EXTENSION
					type = MinecraftDataPins.PinnedFile.EXTENSION
				}
			}
		}

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
		dependencies.add("generatorImplementation", catalog.library("jackson-toml"))

		val arguments = objects.newInstance<MinecraftDataGeneratorArguments>().apply {
			this.kind.set(kind)
			pins.set(pinsFile)
			this.minecraftData.from(minecraftData)
			this.inputs.set(inputs)
			destination.set(layout.buildDirectory.dir("generated/minecraft-data"))
			this.packagePath.set(packagePath)
		}
		val generate = tasks.register<JavaExec>("generate${kind.stem}") {
			description = "Generates the ${kind.subject} resources from the minecraft-data files ${kind.pinsFile} pins."
			classpath = generatorSet.runtimeClasspath
			mainClass.set(generator)
			argumentProviders.add(arguments)
		}
		main.resources.srcDir(generate.map { arguments.destination.get() })

		val test = sourceSets.getByName("test")
		test.compileClasspath += generatorSet.output
		test.runtimeClasspath += generatorSet.output
		configurations.getByName("testImplementation").extendsFrom(configurations.getByName("generatorImplementation"))

		val check = tasks.register<CheckMinecraftDataReleases>("check${kind.stem}Releases") {
			description = "Checks that ${kind.pinsFile} lists the protocol of every protocol library release."
			group = LifecycleBasePlugin.VERIFICATION_GROUP
			this.kind.set(kind)
			pins.set(pinsFile)
			libraryReleases.from(layout.settingsDirectory.file(LIBRARY_RELEASES))
			marker.set(layout.buildDirectory.file("checks/${kind.pinsFile.removeSuffix(".toml")}-releases"))
		}
		tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(check) }

		tasks.register<PinMinecraftData>("pin${kind.stem}") {
			group = GROUP
			description = "Resolves the minecraft-data release ${kind.pinsFile} names and pins the ${kind.subject} files of every protocol."
			this.kind.set(kind)
			pins.set(pinsFile)
		}
	}

	private companion object {
		const val GROUP = "minecraft data"
		const val LIBRARY_RELEASES = "anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml"
	}
}
