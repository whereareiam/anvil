plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesApi)
}
