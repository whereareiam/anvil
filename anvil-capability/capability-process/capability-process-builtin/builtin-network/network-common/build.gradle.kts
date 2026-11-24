plugins {
	id("module-java")
}

description = "Host provider of the built-in network capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkAgent)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkApi)
	compileOnly(projects.anvilProcess.processAgent.agentApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkAgent)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkApi)
	testImplementation(projects.anvilProcess.processAgent.agentApi)
}
