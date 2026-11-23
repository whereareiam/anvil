import me.whereareiam.anvil.buildlogic.minecraftdata.MinecraftDataKind

plugins {
	id("module-edition")
	id("module-minecraft-data")
}

architecture {
	dependsOnApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Packet names of every Minecraft: Java Edition release Anvil supports, looked up by connection state and packet ID"

dependencies {
	api(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
}

// packet-data.toml pins the PrismarineJS minecraft-data protocol.json of each protocol, whose packet mappings name
// every packet by connection state and ID.
minecraftData.generate(
	MinecraftDataKind(
		subject = "packet data",
		stem = "PacketData",
		pinsFile = "packet-data.toml",
		files = listOf(MinecraftDataKind.File("packets", "protocol")),
	),
	generator = "me.whereareiam.anvil.edition.java.data.packet.PacketDataGenerator",
	packagePath = "me/whereareiam/anvil/edition/java/data/packet",
)
