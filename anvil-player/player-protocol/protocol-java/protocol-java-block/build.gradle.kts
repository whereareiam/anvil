import me.whereareiam.anvil.buildlogic.block.BlockDataGeneratorArguments
import me.whereareiam.anvil.buildlogic.block.BlockDataPins
import me.whereareiam.anvil.buildlogic.block.BlockDataPinsFile
import me.whereareiam.anvil.buildlogic.block.CheckBlockDataReleases
import me.whereareiam.anvil.buildlogic.block.PinBlockData

plugins {
	id("module-api")
}

description = "Block data of every Minecraft: Java Edition release Anvil supports, looked up by global block state ID"

dependencies {
	api(projects.anvilApi)
}

// block-data.toml pins the PrismarineJS minecraft-data files of each protocol and block-facts.toml holds what that
// data does not carry. Gradle resolves the pinned files through the settings' minecraft-data repository, so they are
// fetched once and offline builds work; the generator's arguments match them to their pins by sha256.
val blockDataPins = layout.projectDirectory.file("block-data.toml")
val pinned = BlockDataPinsFile.read(blockDataPins.asFile)
val minecraftData by configurations.creating {
	description = "The minecraft-data files block-data.toml pins."
	isCanBeConsumed = false
	isTransitive = false
}

dependencies {
	minecraftData(pinned.module) {
		pinned.files.forEach { file ->
			artifact {
				name = file.name
				classifier = file.directory
				extension = BlockDataPins.PinnedFile.EXTENSION
				type = BlockDataPins.PinnedFile.EXTENSION
			}
		}
	}
}

// The generator is Java beside the data's own model, so the shipped data, its enums and the codec that writes and
// reads it exist once. It compiles against the main classes and writes the main resources; tests use it too.
val generator = sourceSets.create("generator") {
	compileClasspath += files(sourceSets.main.map { it.java.classesDirectory })
	runtimeClasspath += files(sourceSets.main.map { it.java.classesDirectory })
}

configurations.named("generatorImplementation") {
	extendsFrom(configurations.api.get(), configurations.implementation.get())
}

configurations.named("generatorCompileOnly") {
	extendsFrom(configurations.compileOnly.get())
}

dependencies {
	"generatorImplementation"(libs.jackson.databind)
	"generatorImplementation"(libs.jackson.toml)
}

val generatorArguments = objects.newInstance<BlockDataGeneratorArguments>().apply {
	pins.set(blockDataPins)
	facts.set(layout.projectDirectory.file("block-facts.toml"))
	minecraftData.from(configurations.named("minecraftData"))
	destination.set(layout.buildDirectory.dir("generated/block-data"))
	packagePath.set("me/whereareiam/anvil/protocol/java/block")
}

val generateBlockData = tasks.register<JavaExec>("generateBlockData") {
	description = "Generates the block data resources from the minecraft-data files block-data.toml pins."
	classpath = generator.runtimeClasspath
	mainClass.set("me.whereareiam.anvil.protocol.java.block.BlockDataGenerator")
	argumentProviders.add(generatorArguments)
}

sourceSets.main {
	resources.srcDir(generateBlockData.map { generatorArguments.destination.get() })
}

sourceSets.test {
	compileClasspath += generator.output
	runtimeClasspath += generator.output
}

configurations.named("testImplementation") {
	extendsFrom(configurations.named("generatorImplementation").get())
}

val checkBlockDataReleases = tasks.register<CheckBlockDataReleases>("checkBlockDataReleases") {
	description = "Checks that block-data.toml lists the protocol of every protocol library release."
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	pins.set(blockDataPins)
	libraryReleases.from(layout.settingsDirectory.file(
		"anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml"))
	marker.set(layout.buildDirectory.file("checks/block-data-releases"))
}

tasks.named("check") {
	dependsOn(checkBlockDataReleases)
}

tasks.register<PinBlockData>("pinBlockData") {
	group = "block"
	description = "Resolves the minecraft-data release block-data.toml names and pins the block data files of every protocol."
	pins.set(blockDataPins)
}
