plugins {
	id("module-java")
}

architecture {
	sharedApis = setOf(projects.anvilProcess.processAgent.agentApi.path)
}

description = "Client side of the agent protocol: connections to the agents in running processes"
