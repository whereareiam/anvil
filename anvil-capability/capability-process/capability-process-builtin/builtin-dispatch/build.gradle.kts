plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityProcess.capabilityProcessApi.path,
	)
}

description = "Wiring bundle for the built-in dispatch capability"

dependencies {
	api(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
}
