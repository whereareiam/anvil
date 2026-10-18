plugins {
	id("module-adapter-segment")
}

// MCProtocolLib 1.21 brings its own profiles and session service instead of mcauthlib's and sends the view with an item
// use; the segment takes every 1.20.6 class but the sessions and the interaction translation and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1206)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InteractionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.TcpClientSessions",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
