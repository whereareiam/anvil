plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Embedded agent endpoints, native platform access, and operation provider contracts"

toolkitPublish {
	artifactId.set("agent-server-api")
}

dependencies {
	api(projects.anvilAgent.agentApi)
}
