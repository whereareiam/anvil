plugins {
	id("module-java")
	id("module-java-legacy")
}

description = "Authenticated agent endpoint and operation handlers embedded in Minecraft processes"

dependencies {
	compileOnly(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)
	compileOnly(libs.jackson.databind)

	testImplementation(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)
	testImplementation(projects.anvilProcess.processProtocol.protocolHost.protocolHostApi)
	testImplementation(projects.anvilProcess.processProtocol.protocolHost.protocolHostCommon)
	testImplementation(libs.jackson.databind)
}
