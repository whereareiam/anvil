plugins {
	id("module-block-data")
	id("module-edition")
}

description = "Block data of every Minecraft: Java Edition release Anvil supports, looked up by global block state ID"

dependencies {
	api(projects.anvilApi)
}

// vanilla/<protocol>.blocks is the block data the physics harness's generateVanillaData read from each release's
// vanilla client; ./gradlew -p anvil-testkit/physics/harness generateVanillaData writes it again.
blockData.generate(
	generator = "me.whereareiam.anvil.edition.java.data.block.BlockDataGenerator",
	packagePath = "me/whereareiam/anvil/edition/java/data/block",
	vanilla = layout.projectDirectory.dir("vanilla"),
)
