import me.whereareiam.anvil.buildlogic.module.TestFileArgument

plugins {
	id("module-java")
	id("test-fixtures-consumer")
}

description = "Provider discovery, capability composition, and cross-module runtime integration tests"

dependencies {
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityDefault)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilPlayer.playerApi)
	testImplementation(projects.anvilPlayer.playerCommon)
	testImplementation(projects.anvilProcess.processApi)

	// The launcher's shaded JAR carries the planner at runtime; tests only compile against it.
	testCompileOnly(projects.anvilProcess.processCommon)

	testRuntimeOnly(projects.anvilPlayer.playerLibrary.libraryMcprotocol)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.bukkitAgent)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.paperProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.spigotProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeProvider)
	testRuntimeOnly(projects.anvilProcess.processPlatform.platformVelocity.velocityProvider)
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
