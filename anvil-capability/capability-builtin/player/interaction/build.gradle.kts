plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityProtocolApi.path,
	)
}

description = "Public wiring bundle for the built-in interaction capability"

dependencies {
	api(projects.anvilCapability.capabilityProtocolApi)
}
