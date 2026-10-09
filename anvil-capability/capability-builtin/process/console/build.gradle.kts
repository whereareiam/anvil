plugins {
	id("module-capability")
}

description = "Agent wiring bundle for the built-in console capability"

dependencies {
	api(projects.anvilCapability.capabilityAgentApi)

	implementation(projects.anvilAgent.agentApi)
}
