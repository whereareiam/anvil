plugins {
	id("api")
}

description = "Host-side agent connections, discovery, and borrowed client contracts"

toolkitPublish {
	artifactId.set("agent-client-api")
}

dependencies {
	api(projects.anvilAgent.agentApi)
}
