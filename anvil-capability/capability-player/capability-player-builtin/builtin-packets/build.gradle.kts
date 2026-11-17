plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	dependsOnApis = setOf(
		projects.anvilPlayer.playerApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaJson.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaPacket.path,
	)
}

description = "Public wiring bundle for the built-in packets capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)

	// The host provider reads and writes recordings in the Java protocol's JSON form.
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaJson)
}
