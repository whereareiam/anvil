plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityProcess.capabilityProcessApi.path,
	)
	// The agent channel's operations are agent API descriptors, and its handlers implement the agent server API; the
	// NeoForge side reads the channels through the NeoForge agent's service.
	dependsOnApis = setOf(
		projects.anvilProcess.processAgent.agentApi.path,
		projects.anvilProcess.processAgent.agentServer.serverApi.path,
		projects.anvilProcess.processAgent.agentServer.serverNeoforgeApi.path,
	)
}

description = "Wiring bundle for the built-in network capability"

dependencies {
	api(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
}
