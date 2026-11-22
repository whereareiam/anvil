plugins {
	id("module-adapter-segment")
}

// MCProtocolLib 1.21 brings its own profiles and session service instead of mcauthlib's, sends the view with an item
// use, switches to the configuration before it reports the login acknowledgement written, names attribute modifiers by
// keys instead of UUIDs and splits the protocol's state by direction; the segment takes every 1.20.6 class but the
// sessions with their protocol and their frames, the configuration start and the interaction and attribute
// translations and brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1206)
	replaces(
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.AttributePackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ClientConfiguration",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ClientProtocol",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.FramedClientSession",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.InteractionPackets",
		"me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.TcpClientSessions",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolNetty)

	testImplementation(testFixtures(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi))
}
