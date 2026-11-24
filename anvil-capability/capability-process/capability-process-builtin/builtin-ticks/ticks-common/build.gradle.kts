plugins {
	id("module-java")
}

description = "Host provider of the built-in ticks capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinTicks.ticksAgent)
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinTicks.ticksApi)
	compileOnly(projects.anvilProcess.processAgent.agentApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinTicks.ticksAgent)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinTicks.ticksApi)
	testImplementation(projects.anvilProcess.processAgent.agentApi)
}
