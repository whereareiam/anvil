plugins {
	id("module-adapter-segment")
}

// Minecraft 26.3 accepts a teleport with the position and view it results in, plays arm swings on the server and
// follows an attack with a punch, types registry data as any tag, which the configuration and connection translations
// read, and syncs an entity's position in steps. The segment takes every 26.1 class but those that changed and brings
// its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v2612)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ConfigurationPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.EntityPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.TeleportListener",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2.InteractionPackets",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
