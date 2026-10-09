plugins {
	id("net.neoforged.moddev")
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for NeoForge"

// The agent compiles against the oldest NeoForge release that Anvil's version data lists and uses only names that
// later releases keep, so one mod JAR loads on every supported release.
neoForge {
	version = libs.versions.neoforge.get()
}

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}
