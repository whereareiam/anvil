plugins {
	id("module-java")
	id("packaging-publication")
	id("packaging-shaded-jar")
	id("module-java-legacy")
	id("packaging-version-stamp")
}

architecture {
	sharedApis = setOf(projects.anvilAgent.agentApi.path)
}

description = "Authenticated agent endpoint and operation handlers embedded in Minecraft processes"

toolkitPublish {
	artifactId.set("agent")
}

dependencies {
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)

	embedded(projects.anvilAgent.agentApi) { isTransitive = false }
	embedded(projects.anvilAgent.agentServer.agentServerApi) { isTransitive = false }
	embedded(libs.jackson.databind)

	testImplementation(projects.anvilAgent.agentClient)
	testImplementation(projects.anvilAgent.agentClient.clientApi)
	testImplementation(projects.anvilAgent.agentServer.agentServerApi)
}
