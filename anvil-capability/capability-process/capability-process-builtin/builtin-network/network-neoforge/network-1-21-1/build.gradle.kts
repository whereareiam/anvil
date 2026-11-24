plugins {
	id("net.neoforged.moddev")
	id("module-java")
	id("packaging-publication")
}

description = "NeoForge side of the built-in network capability for Minecraft 1.21.1 until 1.21.11"

toolkitPublish {
	artifactId.set("builtin-network-neoforge-1.21.1")
}

neoForge {
	version = libs.versions.neoforge.get()
}

// NeoForge keeps the channels it registered to itself; the NeoForge agent reads them through an accessor of its own and
// offers them as a service of its API, which this side asks for.
dependencies {
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkAgent)
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverApi)
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverNeoforgeApi)
}
