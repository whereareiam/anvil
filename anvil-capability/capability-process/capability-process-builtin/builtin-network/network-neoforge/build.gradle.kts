plugins {
	id("module-assembly")
}

// Minecraft renamed the names the network side uses (resource locations became identifiers) in 1.21.11, so the side is
// one part for each change, each compiled against the first NeoForge release it serves: Minecraft 1.21.1, and 1.21.11
// on. Every part goes into every NeoForge process; the one whose release range holds the server's version installs.
description = "NeoForge side of the built-in network capability: one part for each change of Minecraft's names"

dependencies {
	runtimeOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkNeoforge.network1211)
	runtimeOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinNetwork.networkNeoforge.network12111)
}
