plugins {
    id("unit")
}

description = "Sessions, capabilities, extensions, and routing against real Minecraft servers and proxies"

dependencies {
    testImplementation(projects.anvilCapability.capabilityBuiltin.default)
    testImplementation(projects.anvilEngine)
    testImplementation(projects.anvilIntegration.junit.extension)
    testImplementation(projects.anvilLauncher)
    testImplementation(projects.anvilTesting.testingFixtures.fixturesExternalExtension)

    testRuntimeOnly(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordAgent)
    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
    testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
    testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityAgent)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
}

val serverPlugin = configurations.create("serverPlugin") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
}

dependencies {
    add(serverPlugin.name, projects.anvilTesting.testingFixtures.fixturesServerPlugin)
}

val fullTesting = providers.gradleProperty("anvil.testMode").orElse("unit")
    .map { it.equals("full", ignoreCase = true) }
val matrixFilter = providers.gradleProperty("anvilMatrixFilter").orElse(".*")
val testSuite = providers.gradleProperty("anvilTestSuite").orElse("all")

tasks.test {
    val enabledForRun = fullTesting.get()
    onlyIf("Real server tests require -Panvil.testMode=full") { enabledForRun }
    inputs.property("anvilTestMode", fullTesting)
    inputs.property("anvilMatrixFilter", matrixFilter)
    inputs.property("anvilTestSuite", testSuite)
    when (val suite = testSuite.get()) {
        "all" -> Unit
        "compatibility" -> filter.includeTestsMatching("*.ProxyServerCompatibilitySystemTest")
        "behavior" -> filter.excludeTestsMatching("*.ProxyServerCompatibilitySystemTest")
        else -> throw GradleException("Unknown anvilTestSuite '$suite'; expected all, compatibility, or behavior")
    }
    inputs.files(serverPlugin).withPropertyName("serverPlugin")
    systemProperty("anvil.testing.serverPlugin", serverPlugin.singleFile.absolutePath)
    systemProperty("anvil.matrix.filter", matrixFilter.get())
    systemProperty("anvil.eula.accepted", "true")
}
