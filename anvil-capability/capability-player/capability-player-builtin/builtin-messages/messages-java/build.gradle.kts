plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in messages capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesApi)
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	implementation(projects.anvilPlayer.playerApi)

	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
}
