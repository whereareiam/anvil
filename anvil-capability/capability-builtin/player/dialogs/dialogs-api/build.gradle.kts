plugins {
	id("module-api")
}

description = "Public API and protocol-library port of the built-in dialogs capability"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilCapability.capabilityBuiltin.player.commands.commandsApi)
}
