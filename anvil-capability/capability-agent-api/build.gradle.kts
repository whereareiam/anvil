plugins {
	id("api")
}

description = "Agent-backed process and player capability provider contracts"

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
