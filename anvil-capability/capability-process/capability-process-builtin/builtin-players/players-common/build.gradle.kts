plugins {
	id("module-java")
}

description = "Host provider of the built-in players capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinPlayers.playersApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinPlayers.playersApi)
}
