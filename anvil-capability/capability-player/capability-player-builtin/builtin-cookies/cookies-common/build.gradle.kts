plugins {
	id("module-java")
}

description = "Host provider of the built-in cookies capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinCookies.cookiesApi)
	compileOnly(projects.anvilPlayer.playerApi)
	compileOnly(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinCookies.cookiesApi)
	testImplementation(projects.anvilPlayer.playerApi)
	testImplementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
