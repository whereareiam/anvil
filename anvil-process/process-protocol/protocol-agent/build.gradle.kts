plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("module-java-legacy")
	id("packaging-version-stamp")
}

architecture {
	sharedApis = setOf(projects.anvilProcess.processProtocol.processProtocolApi.path)
}

description = "Agent runtime that platform agents embed: the endpoint, its contracts and the shared operations"

toolkitPublish {
	artifactId.set("agent")
}

dependencies {
	embedded(projects.anvilProcess.processProtocol.processProtocolApi) { isTransitive = false }
	embedded(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi) { isTransitive = false }
	embedded(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentCommon) { isTransitive = false }
	embedded(libs.jackson.databind)
}
