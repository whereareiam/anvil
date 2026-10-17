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
	)
}

description = "Public wiring bundle for the built-in commands capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
}
