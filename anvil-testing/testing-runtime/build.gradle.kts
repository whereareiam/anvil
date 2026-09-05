plugins {
    id("unit")
}

description = "Provider discovery, capability composition, and cross-module runtime integration tests"

dependencies {
    testImplementation(projects.anvilCapability.capabilityBuiltin.default)
    testImplementation(projects.anvilEngine)
    testImplementation(projects.anvilLauncher)
    testImplementation(projects.anvilTesting.testingFixtures.fixturesExternalExtension)

    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
    testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
    testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
}
