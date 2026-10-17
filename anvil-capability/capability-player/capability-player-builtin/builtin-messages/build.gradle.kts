plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
}

description = "Public wiring bundle for the built-in messages capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
}
