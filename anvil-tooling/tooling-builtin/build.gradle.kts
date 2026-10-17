plugins {
	id("module-assembly")
}

description = "Optional tooling actions and observations for Anvil's built-in capabilities"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesApi)
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinSession.sessionApi)
	implementation(projects.anvilTooling.toolingExtensionApi)
}
