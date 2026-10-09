import me.whereareiam.anvil.buildlogic.jvm.TestFileArgument

plugins {
	id("module-java")
	id("test-fixtures-consumer")
}

description = "Provider discovery, capability composition, and cross-module runtime integration tests"

dependencies {
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilPlatform.platformApi)
	testImplementation(projects.anvilProtocol)
	testImplementation(projects.anvilProtocol.protocolApi)

	// The launcher's shaded JAR carries the planner at runtime; tests only compile against it.
	testCompileOnly(projects.anvilPlatform.platformPlanning)

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

// The Java guide's table of Java versions per platform version must equal the providers' version data.
tasks.named<Test>("test") {
	jvmArgumentProviders.add(objects.newInstance<TestFileArgument>().apply {
		property.set("anvil.docs.javaGuide")
		file.set(layout.settingsDirectory.file("docs/content/building-blocks/environments/provisioning/java/index.md"))
	})
}
