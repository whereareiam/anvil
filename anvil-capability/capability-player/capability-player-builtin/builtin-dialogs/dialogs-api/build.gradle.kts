plugins {
	id("module-api")
}

description = "Public API and host-to-worker channel contract of the built-in dialogs capability"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinCommands.commandsApi)
	api(projects.anvilPlayer.playerApi)
}
