plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("module-java-legacy")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for Paper and Spigot"

toolkitPublish {
	artifactId.set("platform-bukkit-agent")
}

dependencies {
	compileOnly(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)
	compileOnly(libs.bukkit.spigot)

	embedded(projects.anvilProcess.processProtocol.protocolAgent) { isTransitive = false }
}

// The agent uses only API names that Mojang and Spigot mappings share, so Paper loads it without remapping.
// The shaded JAR inherits this manifest.
tasks.named<Jar>("jar") {
	manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
