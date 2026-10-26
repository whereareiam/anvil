plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	// The host provider names block states through the block data of the player's release.
	dependsOnApis = setOf(
		projects.anvilPlayer.playerApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaData.path,
	)
}

description = "Public wiring bundle for the built-in world capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)

	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaData)
}
