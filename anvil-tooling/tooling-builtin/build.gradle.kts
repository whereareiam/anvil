plugins {
	id("module-assembly")
}

description = "Optional tooling actions and observations for Anvil's built-in capabilities"

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.client.messages.messagesApi)
	implementation(projects.anvilCapability.capabilityBuiltin.client.session.sessionApi)
	implementation(projects.anvilTooling.toolingExtensionApi)
}
