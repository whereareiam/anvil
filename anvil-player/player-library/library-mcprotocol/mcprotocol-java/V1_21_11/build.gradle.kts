plugins {
	id("module-adapter-segment")
}

// MCProtocolLib 1.21.11 keeps the 1.21 clients, connection and screen translations and the release-neutral parts of
// the other areas; the segment replaces the classes whose release changed or that no longer link against it.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1211)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.ChatPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.ClientPacketListener",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.CommandPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.CookiePackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.EntityDataTable",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.EntityPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.InteractionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.InventoryPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.PlayerPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.SoundPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.TcpClientSessions",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
