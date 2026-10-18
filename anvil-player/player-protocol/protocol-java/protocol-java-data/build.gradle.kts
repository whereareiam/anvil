import me.whereareiam.anvil.buildlogic.data.GenerateBlockData
import me.whereareiam.anvil.buildlogic.data.PinBlockData

plugins {
	id("module-api")
}

description = "Block data of every Minecraft: Java Edition release Anvil supports, looked up by global block state ID"

dependencies {
	api(projects.anvilApi)
}

// block-data.toml pins the PrismarineJS minecraft-data files of each release and block-facts.toml holds what that
// data does not carry; the build downloads the pinned files, checks their sha256 and generates the resources.
val blockDataPins = layout.projectDirectory.file("block-data.toml")
val generateBlockData = tasks.register<GenerateBlockData>("generateBlockData") {
	description = "Generates the block data resources from the minecraft-data files block-data.toml pins."
	pins.set(blockDataPins)
	facts.set(layout.projectDirectory.file("block-facts.toml"))
	packagePath.set("me/whereareiam/anvil/protocol/java/data")
	destination.set(layout.buildDirectory.dir("generated/block-data"))
}

sourceSets.main {
	resources.srcDir(generateBlockData)
}

tasks.register<PinBlockData>("pinBlockData") {
	group = "data"
	description = "Resolves the minecraft-data release block-data.toml names and pins the block data files of every release."
	pins.set(blockDataPins)
}
