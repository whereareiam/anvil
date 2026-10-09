plugins {
	id("module-java")
}

description = "Library-neutral host provider of the built-in server capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityBuiltin.player.server.serverApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.player.server.serverApi)
}
