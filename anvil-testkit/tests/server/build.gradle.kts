plugins {
	id("unit")
	id("fixtures")
	id("me.whereareiam.anvil")
}

description = "Sessions, capabilities, extensions, and routing against real Minecraft servers and proxies"

dependencies {
	testImplementation(projects.anvilCapability.capabilityAgentApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
	testImplementation(projects.anvilIntegration.integrationJunit)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilTooling.toolingRunner)

	testRuntimeOnly(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
	testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordAgent)
	testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
	testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
	testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
	testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityAgent)
	testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
	testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
	testRuntimeOnly(projects.anvilTooling.toolingBuiltin)
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

anvil {
	fixtures.artifacts().forEach { (name, files) -> artifact("testkit-$name", files) }
}

// The scenario plugin adds published Anvil coordinates, whose artifact IDs differ from project names.
// Resolve them to the current source projects instead of a previously published copy.
configurations.configureEach {
	resolutionStrategy.dependencySubstitution {
		substitute(module("me.whereareiam.anvil:api")).using(project(":anvil-api"))
		substitute(module("me.whereareiam.anvil:launcher")).using(project(":anvil-launcher"))
		substitute(module("me.whereareiam.anvil:tooling-launcher")).using(project(":anvil-tooling:tooling-launcher"))
		substitute(module("me.whereareiam.anvil:tooling-builtin")).using(project(":anvil-tooling:tooling-builtin"))
	}
}

// Scenario definitions live in src/anvil, where the IDE and anvilScenario discover them; the system
// tests drive the same definitions.
sourceSets.test {
	val scenarios = sourceSets["anvil"].output
	compileClasspath += scenarios
	runtimeClasspath += scenarios
}
