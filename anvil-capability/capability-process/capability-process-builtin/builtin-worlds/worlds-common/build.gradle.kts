plugins {
	id("module-java")
}

description = "Host provider of the built-in worlds capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsAgent)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsApi)
	compileOnly(projects.anvilProcess.processProtocol.processProtocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsAgent)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsApi)
	testImplementation(projects.anvilProcess.processProtocol.processProtocolApi)
}
