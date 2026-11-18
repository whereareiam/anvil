plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in interaction capability: its worker extension over the protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInteraction.interactionApi)
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	implementation(projects.anvilPlayer.playerApi)

	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
}
