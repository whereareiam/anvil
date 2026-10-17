plugins {
	id("module-api")
}

description = "Agent-backed process capability provider contracts"

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
