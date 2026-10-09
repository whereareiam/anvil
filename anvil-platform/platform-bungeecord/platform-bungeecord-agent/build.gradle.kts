plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for BungeeCord"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)
	compileOnly(libs.bungeecord)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}
