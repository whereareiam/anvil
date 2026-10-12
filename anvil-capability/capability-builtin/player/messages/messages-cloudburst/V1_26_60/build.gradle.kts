plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.messages.messagesApi)
	implementation(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
}
