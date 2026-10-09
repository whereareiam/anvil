plugins {
	id("assembly")
	id("bundle")
	id("in-server")
	id("descriptor-version")
}

description = "Anvil platform agent for Paper and Spigot"

dependencies {
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)
	compileOnly(libs.bukkit.spigot)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}

// The agent uses only API names that Mojang and Spigot mappings share, so Paper loads it without remapping.
// The shaded JAR inherits this manifest.
tasks.named<Jar>("jar") {
	manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
