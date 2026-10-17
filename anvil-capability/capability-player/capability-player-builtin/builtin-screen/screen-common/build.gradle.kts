plugins {
	id("module-java")
}

description = "Host provider of the built-in screen capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinScreen.screenApi)
	compileOnly(projects.anvilPlayer.playerApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinScreen.screenApi)
	testImplementation(projects.anvilPlayer.playerApi)
}
