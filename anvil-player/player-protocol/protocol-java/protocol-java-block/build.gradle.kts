import me.whereareiam.anvil.buildlogic.minecraftdata.MinecraftDataKind

plugins {
	id("module-minecraft-data")
}

description = "Block data of every Minecraft: Java Edition release Anvil supports, looked up by global block state ID"

dependencies {
	api(projects.anvilApi)
}

// block-data.toml pins the PrismarineJS minecraft-data files of each protocol and block-facts.toml holds what that
// data does not carry.
minecraftData.generate(
	MinecraftDataKind(
		subject = "block data",
		stem = "BlockData",
		pinsFile = "block-data.toml",
		files = listOf(
			MinecraftDataKind.File("blocks", "blocks"),
			MinecraftDataKind.File("collision-shapes", "blockCollisionShapes"),
		),
	),
	generator = "me.whereareiam.anvil.protocol.java.block.BlockDataGenerator",
	packagePath = "me/whereareiam/anvil/protocol/java/block",
	inputs = mapOf("facts" to layout.projectDirectory.file("block-facts.toml")),
)
