plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	// Dialogs check the commands their buttons run through the player's Commands capability, when it has one.
	dependsOnApis = setOf(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinCommands.commandsApi.path)
}

description = "Public wiring bundle for the built-in dialogs capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
}
