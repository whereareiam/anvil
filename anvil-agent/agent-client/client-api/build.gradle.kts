plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Host-side agent connections, discovery, and borrowed client contracts"

toolkitPublish {
	artifactId.set("agent-client-api")
}

dependencies {
	api(projects.anvilAgent.agentApi)
}
