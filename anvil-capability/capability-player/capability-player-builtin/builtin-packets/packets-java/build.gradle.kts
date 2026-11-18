plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in packets capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinPackets.packetsApi)
	implementation(projects.anvilEdition.editionJava.javaData.dataPacket)
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	implementation(projects.anvilPlayer.playerApi)

	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
}
