plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityProtocolApi.path,
	)
	// The cloudburst segment implements the port against the native session of CloudburstMC Protocol clients.
	dependsOnApis = setOf(projects.anvilProtocol.protocolCloudburst.cloudburstApi.path)
}

description = "Public wiring bundle for the built-in interaction capability"

dependencies {
	api(projects.anvilCapability.capabilityProtocolApi)
}
