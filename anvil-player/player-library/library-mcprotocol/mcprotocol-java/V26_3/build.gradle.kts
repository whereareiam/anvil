plugins {
	id("module-adapter-segment")
}

// Minecraft 26.3 plays arm swings on the server, so its client answers nothing on its own and the segment leaves the
// chain's ReleaseAnswers out; it follows an attack with a punch, types registry data as any tag and syncs an entity's
// position in steps. The segment takes every 26.1 class but those that changed and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v2612)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConfigurationPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.EntityPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.PlayerPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.ReleaseAnswers",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2.InteractionPackets",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolNetty)

	testImplementation(testFixtures(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi))
}
