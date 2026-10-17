plugins {
	id("module-java")
}

description = "Dependency-ordered capability composition for player and process owners"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.parameters)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityDefault)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
}
