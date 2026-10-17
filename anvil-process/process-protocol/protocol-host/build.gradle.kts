plugins {
	id("module-java")
}

architecture {
	sharedApis = setOf(projects.anvilProcess.processProtocol.processProtocolApi.path)
}

description = "Host side of the agent protocol: connections to the agents in running processes"
