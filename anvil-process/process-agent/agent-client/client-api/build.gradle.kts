plugins {
	id("module-api")
}

description = "Client-side agent connections, discovery, and borrowed client contracts"

toolkitPublish {
	artifactId.set("agent-client-api")
}

dependencies {
	api(projects.anvilProcess.processAgent.agentApi)
}
