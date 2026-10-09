plugins {
	id("assembly")
	id("bundle")
	id("descriptor-version")
}

description = "Anvil platform agent for Velocity"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)
	compileOnly(libs.velocity)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }

	annotationProcessor(libs.velocity)
}
