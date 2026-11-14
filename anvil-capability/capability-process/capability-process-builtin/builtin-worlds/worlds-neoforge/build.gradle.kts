plugins {
	id("module-assembly")
}

// NeoForge renamed the Minecraft names the worlds side uses between its releases, so the side is one part for each
// change, each compiled against the first NeoForge release it serves: Minecraft 1.21.1, and 1.21.11 on. Every part
// goes into every NeoForge process; the one whose release range holds the server's Minecraft version installs.
description = "NeoForge side of the built-in worlds capability: one part for each change of Minecraft's names"

dependencies {
	runtimeOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsNeoforge.release1211)
	runtimeOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinWorlds.worldsNeoforge.release12111)
}
