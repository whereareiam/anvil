plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryApi)

	// The CloudburstMC Protocol worker supplies the native session interface together with its client segment.
	compileOnly(projects.anvilProtocol.protocolCloudburst.cloudburstApi)

	testImplementation(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
}
