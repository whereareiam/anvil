plugins {
	id("module-adapter-segment")
}

// MCProtocolLib 1.19.4 signs chat, merged the mob and painting spawns into the entity spawn, resolves sounds itself,
// sends the block-change sequence with uses, names dimension types by key, reads chunks from buffers and reports
// disconnect reasons as components; the segment takes every 1.18.2 class but the translations of these areas and
// brings its own.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v1182)
	replaces(
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ChatPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.CommandPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ConnectionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.EntityDataTable",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.EntityPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.InteractionPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.SoundPackets",
		"me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.WorldPackets",
	)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}

// The release bundles packetlib, which opens one io_uring ring per event-loop thread, and a ring for every core can
// exceed the locked memory a machine allows; tests that connect use as few threads as workers do.
tasks.test {
	systemProperty("io.netty.eventLoopThreads", 4)
}
