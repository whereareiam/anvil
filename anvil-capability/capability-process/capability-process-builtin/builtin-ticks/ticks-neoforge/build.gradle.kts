plugins {
	id("net.neoforged.moddev")
	id("module-java")
	id("packaging-publication")
}

// Minecraft kept the names of its tick rate manager and NeoForge those of its server tick event from 1.21.1 on, so
// one side compiled against the oldest NeoForge release Anvil supports serves every release, as the NeoForge agent does.
description = "NeoForge side of the built-in ticks capability: its agent extension over Minecraft's tick rate manager"

neoForge {
	version = libs.versions.neoforge.get()
}

dependencies {
	compileOnly(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinTicks.ticksAgent)
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverApi)
}
