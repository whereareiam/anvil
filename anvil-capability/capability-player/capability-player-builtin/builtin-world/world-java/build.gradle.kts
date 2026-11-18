plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in world capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinWorld.worldApi)
	implementation(projects.anvilEdition.editionJava.javaData.dataBlock)
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	implementation(projects.anvilPlayer.playerApi)

	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
}
