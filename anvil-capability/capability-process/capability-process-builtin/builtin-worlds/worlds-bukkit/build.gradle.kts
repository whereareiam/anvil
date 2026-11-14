plugins {
	id("module-java")
	id("module-java-legacy")
	id("packaging-publication")
}

description = "Paper and Spigot side of the built-in worlds capability: its agent extension over the Bukkit API"

dependencies {
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsAgent)
	compileOnly(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)
	compileOnly(libs.bukkit.spigot)

	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsAgent)
	testImplementation(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)
	testImplementation(libs.bukkit.spigot)
}
