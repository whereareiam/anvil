import me.whereareiam.anvil.buildlogic.fixture.LiveMatrixReleases
import me.whereareiam.anvil.buildlogic.fixture.PhysicsMatrix

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
	testImplementation(projects.anvilProcess.processApi)
	testImplementation(projects.anvilTooling.toolingRunner)

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
// The live matrix lists the releases, Spigot and proxy versions and NeoForge releases that run against real servers.
val liveMatrix = layout.projectDirectory.file("live-matrix.toml")
val releases = LiveMatrixReleases.parse(providers.fileContents(liveMatrix).asText.get())
// The physics matrix lists the releases whose players run client physics and pins the Grim and Sonar plugins.
val physicsMatrix = layout.projectDirectory.file("physics-matrix.toml")
val physics = PhysicsMatrix.parse(providers.fileContents(physicsMatrix).asText.get())
// The judges of the physics suite to run, as a comma-separated list of `vanilla`, `grim` and `sonar`; all when empty.
val physicsJudges = providers.gradleProperty("anvilPhysicsJudges").orElse("")
// Plans the live matrix without starting a process to check that verified data lists exactly what it runs.
val matrixDataTest = "*.CompatibilityScenarioFactoryTest"

// Each suite is one package of the tests and runs only with -Panvil.testMode=full: `release` against the server of
// one release, `platform` on Spigot, the proxies and NeoForge, and `behavior` for lifecycles, sessions and extensions.
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

// The physics suite runs on demand only: neither `test` nor `check` depends on it. Each release's task downloads the
// pinned Grim and Sonar plugins, which no other task needs.
val grim = fixtures.remote("grim", physics.grim.url, physics.grim.sha256)
val sonar = fixtures.remote("sonar", physics.sonar.url, physics.sonar.sha256)
val physicsTests = physics.releases.map { release ->
	tasks.register<Test>("physicsTest-$release") {
		description = "Runs the client physics journeys of Minecraft $release against Paper, Grim and Sonar."
		liveSuite("physics")
		systemProperty("anvil.release", release)
		jvmArgumentProviders.add(grim)
		jvmArgumentProviders.add(sonar)
		inputs.property("anvilPhysicsJudges", physicsJudges)
		val judges = physicsJudges.get().split(',').map(String::trim).filter(String::isNotEmpty)
		useJUnitPlatform {
			if (judges.isNotEmpty()) includeTags(*judges.toTypedArray())
		}
	}
}
// One release after another: each release's task keeps three servers for its JVM.
physicsTests.zipWithNext().forEach { (earlier, later) -> later.configure { mustRunAfter(earlier) } }
tasks.register("physicsTest") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Runs the client physics journeys of every release of the physics matrix, one release at a time."
	dependsOn(physicsTests)
}

// The live suites have tasks of their own; `test` runs them all with -Panvil.testMode=full.
tasks.test {
	onlyIf("The live suites run in releaseTest, platformTest and behaviorTest") { false }
	dependsOn(releaseTest, platformTest, behaviorTest)
}

// The matrix data check starts no server, so every build runs it, unlike the real-server tests above.
val matrixTest = tasks.register<Test>("matrixTest") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Checks that the verified platform and protocol data equal the live matrix."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	filter {
		includeTestsMatching(matrixDataTest)
	}
}

tasks.check {
	dependsOn(matrixTest)
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

// The scenario factories read the live and physics matrices beside their own classes.
tasks.named<ProcessResources>("processAnvilResources") {
	from(files(liveMatrix, physicsMatrix)) {
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
