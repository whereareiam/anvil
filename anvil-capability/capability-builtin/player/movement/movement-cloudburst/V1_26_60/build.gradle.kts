plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.movement.movementApi)
	implementation(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
}
