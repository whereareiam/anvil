plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	dependsOnApis = setOf(
		projects.anvilEdition.editionJava.javaData.dataPacket.path,
		projects.anvilEdition.editionJava.javaProtocol.protocolApi.path,
		projects.anvilEdition.editionJava.javaProtocol.protocolJson.path,
		projects.anvilPlayer.playerApi.path,
	)
}

description = "Public wiring bundle for the built-in packets capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)

	// The host provider reads and writes recordings in the Java protocol's JSON form.
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolJson)
}
