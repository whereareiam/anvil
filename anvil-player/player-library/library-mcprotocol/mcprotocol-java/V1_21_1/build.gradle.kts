plugins {
	id("module-adapter-segment")
}

// MCProtocolLib 1.21 brings its own profiles and session service instead of mcauthlib's, sends the view with an item use
// and switches to the configuration before it reports the login acknowledgement written, and it names attribute modifiers
// by keys instead of UUIDs; the segment takes every 1.20.6 class but the sessions with their protocol, the configuration
// start and the interaction and attribute translations and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1206)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.AttributePackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ClientConfiguration",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ClientProtocol",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InteractionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.TcpClientSessions",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
