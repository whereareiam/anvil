plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for BungeeCord"

toolkitPublish {
	artifactId.set("platform-bungeecord-agent")
}

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverApi)
	compileOnly(libs.bungeecord)

	embedded(projects.anvilProcess.processAgent.agentServer) { isTransitive = false }
}
