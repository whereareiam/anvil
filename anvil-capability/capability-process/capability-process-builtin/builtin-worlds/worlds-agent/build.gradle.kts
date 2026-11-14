plugins {
	id("module-api")
	id("module-java-legacy")
}

description = "Agent channel of the built-in worlds capability: the operations and payloads its host and platform sides share"

// The operations are agent API descriptors, which the host provider reads too, and the handlers implement the agent
// server API. The platform agent that loads the channel's JAR already holds both, and Anvil installs only that JAR.
dependencies {
	api(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)

	testImplementation(libs.jackson.databind)
}
