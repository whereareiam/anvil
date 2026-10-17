plugins {
	id("module-java")
}

description = "Host provider of the built-in dispatch capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityAgentApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityBuiltin.server.dispatch.dispatchApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityAgentApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.server.dispatch.dispatchApi)
}
