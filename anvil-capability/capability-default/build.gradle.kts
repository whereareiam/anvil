plugins {
	id("module-assembly")
}

description = "Default aggregate containing the built-in player and process capabilities"

toolkitPublish {
	artifactId.set("default")
}

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinCommands)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinCookies)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinDialogs)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinEntities)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInteraction)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInventory)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinScreen)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinSession)
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinSounds)
	api(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinDispatch)
	api(projects.anvilCapability.capabilityProcess.capabilityProcessBuiltin.builtinPlayers)
}
