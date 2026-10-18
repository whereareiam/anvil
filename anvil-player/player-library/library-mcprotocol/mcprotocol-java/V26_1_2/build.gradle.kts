plugins {
	id("module-adapter-segment")
}

// Minecraft 26.1 changed only the entity interaction, which sends an attack in a packet of its own, so the segment
// takes every 1.21.11 class but the interaction translations and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v12111)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InteractionTranslation",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.InteractionPackets",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
