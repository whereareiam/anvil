plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	// Interactions name blocks by the World capability's positions.
	dependsOnApis = setOf(
		projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinWorld.worldApi.path,
		projects.anvilPlayer.playerApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi.path,
	)
}

description = "Public wiring bundle for the built-in interaction capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
}
