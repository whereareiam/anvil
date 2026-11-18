plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for Velocity"

toolkitPublish {
	artifactId.set("platform-velocity-agent")
}

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverApi)
	compileOnly(libs.velocity)

	embedded(projects.anvilProcess.processAgent.agentServer) { isTransitive = false }

	annotationProcessor(libs.velocity)
}
