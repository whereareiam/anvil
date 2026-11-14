plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityProcess.capabilityProcessApi.path,
	)
	// The agent channel's operations are agent API descriptors, and its handlers implement the agent server API.
	dependsOnApis = setOf(
		projects.anvilProcess.processProtocol.processProtocolApi.path,
		projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi.path,
	)
}

description = "Wiring bundle for the built-in worlds capability"

dependencies {
	api(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
}
