plugins {
	id("module-adapter-segment")
}

// MCProtocolLib 1.21.11 keeps the client, connection and screen translations of the chain and the release-neutral parts
// of the other areas; the segment replaces the classes whose release changed or that no longer link against it.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1211)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChatPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ClientPacketListener",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CommandPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CookiePackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.EntityDataTable",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.EntityPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InventoryPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.PlayerPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SoundPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.WorldPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.InteractionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.TcpClientSessions",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
