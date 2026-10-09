plugins {
	id("module-java")
}

description = "Dependency-ordered capability composition for player and process owners"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.parameters)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityAgentApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProtocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityAgentApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
}
