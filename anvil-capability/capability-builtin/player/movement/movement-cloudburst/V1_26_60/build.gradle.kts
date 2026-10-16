plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.movement.movementApi)

	// The CloudburstMC Protocol worker supplies the native session interface together with its client segment.
	compileOnly(projects.anvilProtocol.protocolCloudburst.cloudburstApi)

	testImplementation(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
}
