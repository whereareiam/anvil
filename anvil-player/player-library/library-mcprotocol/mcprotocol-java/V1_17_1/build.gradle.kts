plugins {
	id("module-adapter-segment")
}

// Minecraft 1.17 added state IDs to containers, packets of their own to titles and to each change of the world border,
// the long argument to commands, the frozen ticks to every entity's data and the angle to the spawn point, gave the
// player's death a packet of its own, renamed the removal of entities, added the ping and dropped the confirmation of
// container actions, and its library renamed its effects after their vanilla keys; the segment takes every 1.16.5
// class but the translations of these areas and the client information and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1165)
	replaces(
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.BorderPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.CommandPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ConnectionPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.EffectPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.EntityDataTable",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.EntityPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.HealthPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.InventoryPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.OverlayPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ReleaseClientInformation",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.WorldPackets",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolNetty)

	testImplementation(testFixtures(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi))
}
