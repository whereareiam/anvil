plugins {
	id("assembly")
	id("bundle")
	id("descriptor-version")
}

description = "Anvil platform agent for BungeeCord"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)
	compileOnly(libs.bungeecord)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}
