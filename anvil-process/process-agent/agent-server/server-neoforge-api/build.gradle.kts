plugins {
	id("net.neoforged.moddev")
	id("module-api")
}

description = "Services the NeoForge platform agent offers the NeoForge sides of capabilities, over NeoForge's own types"

toolkitPublish {
	artifactId.set("agent-server-neoforge-api")
}

// Compiled against the agent's own NeoForge release, the oldest Anvil supports; the services name only types that
// later releases keep.
neoForge {
	version = libs.versions.neoforge.get()
}
