plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("module-java-legacy")
	id("packaging-version-stamp")
}

architecture {
	sharedApis = setOf(projects.anvilProcess.processAgent.agentApi.path)
}

description = "Agent runtime that platform agents embed: the endpoint, its contracts and the shared operations"

toolkitPublish {
	artifactId.set("agent")
}

dependencies {
	embedded(projects.anvilProcess.processAgent.agentApi) { isTransitive = false }
	embedded(projects.anvilProcess.processAgent.agentServer.serverApi) { isTransitive = false }
	embedded(projects.anvilProcess.processAgent.agentServer.serverCommon) { isTransitive = false }
	embedded(libs.jackson.databind)
}
