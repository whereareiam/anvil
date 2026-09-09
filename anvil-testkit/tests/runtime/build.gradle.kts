plugins {
	id("unit")
	id("fixtures")
}

description = "Provider discovery, capability composition, and cross-module runtime integration tests"

dependencies {
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilPlatform.platformApi)
	testImplementation(projects.anvilProtocol.protocolApi)

	testRuntimeOnly(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
	testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
	testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
	testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
	testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
	testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
}

fixtures {
	extension()
	brokenExtension()
}
