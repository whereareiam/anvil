plugins {
	id("unit")
	id("fixtures")
}

description = "Sessions, capabilities, extensions, and routing against real Minecraft servers and proxies"

dependencies {
	testImplementation(projects.anvilCapability.capabilityAgentApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
	testImplementation(projects.anvilIntegration.junit.extension)
	testImplementation(projects.anvilLauncher)

	testRuntimeOnly(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
	testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordAgent)
	testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
	testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
	testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
	testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityAgent)
	testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
	testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
}

val fullTesting = providers.gradleProperty("anvil.testMode").orElse("unit")
	.map { it.equals("full", ignoreCase = true) }
val matrixFilter = providers.gradleProperty("anvilMatrixFilter").orElse(".*")
val testTags = providers.gradleProperty("anvilTestTags")

tasks.test {
	val enabledForRun = fullTesting.get()
	onlyIf("Real server tests require -Panvil.testMode=full") { enabledForRun }
	inputs.property("anvilTestMode", fullTesting)
	inputs.property("anvilMatrixFilter", matrixFilter)
	useJUnitPlatform {
		testTags.orNull?.let { includeTags(it) }
	}
	systemProperty("anvil.matrix.filter", matrixFilter.get())
	systemProperty("anvil.eula.accepted", "true")
}

fixtures {
	serverPlugin()
	extension()
	observationExtension()
}
