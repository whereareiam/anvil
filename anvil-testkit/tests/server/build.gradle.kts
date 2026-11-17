import me.whereareiam.anvil.buildlogic.matrix.LiveMatrix
import me.whereareiam.anvil.buildlogic.module.TestDirectoryArgument

plugins {
	id("module-java")
	id("test-fixtures-consumer")
	id("me.whereareiam.anvil")
}

description = "Sessions, capabilities, extensions, and routing against real Minecraft servers and proxies"

dependencies {
	"anvilImplementation"(libs.jackson.toml)

	testImplementation(projects.anvilCapability.capabilityDefault)
	testImplementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
	testImplementation(projects.anvilIntegration.integrationJunit)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilPlayer.playerApi)
	testImplementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaBlock)
	testImplementation(projects.anvilProcess.processApi)
	testImplementation(projects.anvilTooling.toolingRunner)
	testImplementation(libs.jackson.toml)

	// The launcher's shaded JAR carries the planner at runtime; the matrix data test only compiles against it.
	testCompileOnly(projects.anvilProcess.processCommon)

	testRuntimeOnly(projects.anvilPlayer.playerLibrary.libraryMcprotocol)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.bukkitAgent)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.paperProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.spigotProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordAgent)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeAgent)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformVelocity.velocityAgent)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformVelocity.velocityProvider)
	testRuntimeOnly(projects.anvilTooling.toolingBuiltin)
}

val fullTesting = providers.gradleProperty("anvil.testMode").orElse("unit")
	.map { it.equals("full", ignoreCase = true) }
val platforms = providers.gradleProperty("anvilPlatforms").orElse("")
// The live matrix at the repository root lists what runs against real servers, and is what the platforms' and the
// protocol libraries' version data call verified; each Paper row is one release.
val liveMatrix = layout.settingsDirectory.file("live-matrix.toml")
val releases = LiveMatrix.parse(providers.fileContents(liveMatrix).asText.get()).minecraftVersions("paper")
// Plans the live matrix without starting a process to check that each scenario runs what the version data verifies.
val matrixDataTest = "*.CompatibilityScenarioFactoryTest"
// Checks without a server that Anvil ships block data for every release of the live matrix, which physics needs.
val blockDataCheck = "*.LiveMatrixBlockDataTest"
// The physics courses, which the physics suite runs and the vanilla client's reference traces were generated from.
val physicsCourses = layout.settingsDirectory.dir("anvil-testkit/physics/courses")

// Each suite is one package of the tests and runs only with -Panvil.testMode=full: `release` against the server of
// one release, `physics` the physics courses on the server of one release, `platform` on Spigot, the proxies and
// NeoForge, and `behavior` for lifecycles, sessions and extensions.
fun Test.liveSuite(suite: String) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	val enabledForRun = fullTesting.get()
	onlyIf("Real server tests require -Panvil.testMode=full") { enabledForRun }
	inputs.property("anvilTestMode", fullTesting)
	filter {
		includeTestsMatching("me.whereareiam.anvil.testkit.tests.server.$suite.*")
	}
	systemProperty("anvil.eula.accepted", "true")
}

// One task per release, so each runs in a JVM of its own that keeps one server for the whole release suite.
val releaseTests = releases.map { release ->
	tasks.register<Test>("releaseTest-$release") {
		description = "Runs every player capability against the Paper server of Minecraft $release."
		liveSuite("release")
		systemProperty("anvil.release", release)
	}
}
val releaseTest = tasks.register("releaseTest") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs the release suite against every release of the live matrix, one release at a time."
	dependsOn(releaseTests)
}
// One task per release, as for the release suite: each runs every physics course on that release's Paper server.
val physicsTests = releases.map { release ->
	tasks.register<Test>("physicsTest-$release") {
		description = "Runs every physics course with client physics on the Paper server of Minecraft $release."
		liveSuite("physics")
		systemProperty("anvil.release", release)
		jvmArgumentProviders.add(objects.newInstance<TestDirectoryArgument>().apply {
			property.set("anvil.physics.courses")
			directory.set(physicsCourses)
		})
	}
}
val physicsTest = tasks.register("physicsTest") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs the physics suite against every release of the live matrix, one release at a time."
	dependsOn(physicsTests)
}
val platformTest = tasks.register<Test>("platformTest") {
	description = "Runs the platform suite on Spigot, the proxies and NeoForge, or on those -PanvilPlatforms lists."
	liveSuite("platform")
	inputs.property("anvilPlatforms", platforms)
	systemProperty("anvil.platforms", platforms.get())
}
val behaviorTest = tasks.register<Test>("behaviorTest") {
	description = "Runs the behaviour suite: lifecycles, sessions, routing and extensions."
	liveSuite("behavior")
}

// The live suites have tasks of their own; `test` runs them all with -Panvil.testMode=full.
tasks.test {
	onlyIf("The live suites run in releaseTest, physicsTest, platformTest and behaviorTest") { false }
	dependsOn(releaseTest, physicsTest, platformTest, behaviorTest)
}

// The matrix data check starts no server, so every build runs it, unlike the real-server tests above.
val matrixTest = tasks.register<Test>("matrixTest") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Checks that every live matrix scenario plans the Java its row names, which the version data verifies."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	filter {
		includeTestsMatching(matrixDataTest)
	}
}

// Like the matrix data check, the block data check starts no server, so every build runs it.
val blockDataTest = tasks.register<Test>("blockDataTest") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Checks that Anvil ships block data for every release of the live matrix, which client physics needs."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	filter {
		includeTestsMatching(blockDataCheck)
	}
}

tasks.check {
	dependsOn(matrixTest, blockDataTest)
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

// The scenario factory reads the live matrix beside its own class.
tasks.named<ProcessResources>("processAnvilResources") {
	from(liveMatrix) {
		into("me/whereareiam/anvil/testkit/tests/server/scenario")
	}
}

// Scenario definitions live in src/anvil, where the IDE and anvilScenarios discover them; the system
// tests drive the same definitions.
sourceSets.test {
	val scenarios = sourceSets["anvil"].output
	compileClasspath += scenarios
	runtimeClasspath += scenarios
}
