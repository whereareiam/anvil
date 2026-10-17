plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(projects.anvilCapability.capabilityApi.path, projects.anvilCapability.capabilityAgentApi.path)
}

description = "Wiring bundle for the built-in console capability"

dependencies {
	api(projects.anvilCapability.capabilityAgentApi)
}
