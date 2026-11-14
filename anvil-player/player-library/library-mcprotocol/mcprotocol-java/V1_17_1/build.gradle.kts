plugins {
	id("module-adapter-segment")
}

// Minecraft 1.17 added state IDs to containers, packets of their own to titles and to each change of the world border,
// the long argument to commands, the frozen ticks to every entity's data and the angle to the spawn point, gave the
// player's death a packet of its own and renamed the removal of entities; the segment takes every 1.16.5 class but the
// translations of these areas and the client's answers and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1165)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.CommandPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.EntityDataTable",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.EntityPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.HealthPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.InventoryPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ReleaseAnswers",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ScreenPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.WorldPackets",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
