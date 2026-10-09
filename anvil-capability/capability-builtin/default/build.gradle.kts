plugins {
	id("assembly")
}

description = "Default aggregate containing the built-in player and process capabilities"

dependencies {
	api(projects.anvilCapability.capabilityBuiltin.player.interaction)
	api(projects.anvilCapability.capabilityBuiltin.player.inventory)
	api(projects.anvilCapability.capabilityBuiltin.player.messages)
	api(projects.anvilCapability.capabilityBuiltin.player.movement)
	api(projects.anvilCapability.capabilityBuiltin.player.server)
	api(projects.anvilCapability.capabilityBuiltin.player.session)
	api(projects.anvilCapability.capabilityBuiltin.process.console)
}
