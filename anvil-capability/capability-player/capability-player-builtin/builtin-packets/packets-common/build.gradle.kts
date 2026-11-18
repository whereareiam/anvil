plugins {
	id("module-java")
}

description = "Host provider of the built-in packets capability, with its recordings and their comparison"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinPackets.packetsApi)
	compileOnly(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	compileOnly(projects.anvilEdition.editionJava.javaProtocol.protocolJson)
	compileOnly(projects.anvilPlayer.playerApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(testFixtures(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi))
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinPackets.packetsApi)
	testImplementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	testImplementation(projects.anvilEdition.editionJava.javaProtocol.protocolJson)
	testImplementation(projects.anvilPlayer.playerApi)
	testImplementation(testFixtures(projects.anvilPlayer.playerApi))
}
