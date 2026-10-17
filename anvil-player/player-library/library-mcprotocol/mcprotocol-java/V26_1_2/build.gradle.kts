plugins {
	id("module-adapter-segment")
}

// Minecraft 26.1 changed only the entity interaction, so the segment takes every 1.21.11 class but its adapter.
segment {
	extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v12111)
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
}
