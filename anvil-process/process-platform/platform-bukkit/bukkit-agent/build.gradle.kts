plugins {
	id("module-assembly")
	id("module-java-legacy")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for Paper and Spigot"

toolkitPublish {
	artifactId.set("platform-bukkit-agent")
}

dependencies {
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverApi)
	compileOnly(libs.bukkit.spigot)

	embedded(projects.anvilProcess.processAgent.agentServer) { isTransitive = false }
}

// The agent uses only API names that Mojang and Spigot mappings share, so Paper loads it without remapping.
// The shaded JAR inherits this manifest.
tasks.named<Jar>("jar") {
	manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
