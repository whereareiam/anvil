plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityProtocolApi.path,
	)
	// Dialogs check the commands their buttons run through the player's Commands capability, when it has one.
	dependsOnApis = setOf(projects.anvilCapability.capabilityBuiltin.client.commands.commandsApi.path)
}

description = "Public wiring bundle for the built-in dialogs capability"

dependencies {
	api(projects.anvilCapability.capabilityProtocolApi)
}
