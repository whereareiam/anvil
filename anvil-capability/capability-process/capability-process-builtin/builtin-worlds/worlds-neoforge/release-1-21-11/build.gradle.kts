plugins {
	id("net.neoforged.moddev")
	id("module-java")
	id("packaging-publication")
}

description = "NeoForge side of the built-in worlds capability for Minecraft 1.21.11 and later"

toolkitPublish {
	artifactId.set("builtin-worlds-neoforge-1.21.11")
}

neoForge {
	version = libs.versions.worldsNeoforge12111.get()
}

dependencies {
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsAgent)
	compileOnly(projects.anvilProcess.processProtocol.protocolAgent.protocolAgentApi)
}
