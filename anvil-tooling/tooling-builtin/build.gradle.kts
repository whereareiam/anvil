plugins {
	id("module-assembly")
}

description = "Optional tooling actions and observations for Anvil's built-in capabilities"

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.messages.messagesApi)
	implementation(projects.anvilCapability.capabilityBuiltin.player.session.sessionApi)
	implementation(projects.anvilTooling.toolingExtensionApi)
}
