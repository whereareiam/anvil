plugins {
	id("module-java")
}

description = "Host provider of the built-in packets capability, with its recordings and their comparison"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinPackets.packetsApi)
	compileOnly(projects.anvilPlayer.playerApi)
	compileOnly(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	compileOnly(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaJson)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(testFixtures(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi))
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinPackets.packetsApi)
	testImplementation(projects.anvilPlayer.playerApi)
	testImplementation(testFixtures(projects.anvilPlayer.playerApi))
	testImplementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	testImplementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaJson)
}
