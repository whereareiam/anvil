plugins {
	id("module-assembly")
}

description = "Default aggregate containing the built-in client and server capabilities"

dependencies {
	api(projects.anvilCapability.capabilityBuiltin.client.commands)
	api(projects.anvilCapability.capabilityBuiltin.client.dialogs)
	api(projects.anvilCapability.capabilityBuiltin.client.interaction)
	api(projects.anvilCapability.capabilityBuiltin.client.inventory)
	api(projects.anvilCapability.capabilityBuiltin.client.messages)
	api(projects.anvilCapability.capabilityBuiltin.client.movement)
	api(projects.anvilCapability.capabilityBuiltin.client.session)
	api(projects.anvilCapability.capabilityBuiltin.server.console)
	api(projects.anvilCapability.capabilityBuiltin.server.players)
}
