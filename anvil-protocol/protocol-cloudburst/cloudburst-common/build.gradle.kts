plugins {
	id("module-java")
	// Worker contract tests start real workers on the release's locked closure instead of downloading it.
	id("test-library-closures")
}

description = "CloudburstMC Protocol worker library, its worker client, Bedrock login and release data, and private account store"

dependencies {
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)
	compileOnly(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
	compileOnly(projects.anvilProtocol.protocolWorker.workerApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	// The launcher binds the worker's capabilities to the native worker.
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilProtocol.protocolApi)
	testImplementation(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
	testImplementation(projects.anvilProtocol.protocolWorker.workerApi)
	// Worker contract tests start real workers through the worker host that the family root depends on.
	testImplementation(projects.anvilProtocol.protocolWorker.workerCommon)

	testRuntimeOnly(projects.anvilProtocol.protocolCloudburst.cloudburstClient)
	testRuntimeOnly(libs.slf4j.simple)
}

// The release data lives at the family root, where build-logic reads it for segments; the runtime reads this copy.
tasks.named<ProcessResources>("processResources") {
	from(layout.projectDirectory.file("../cloudburst-releases.toml")) {
		into("me/whereareiam/anvil/protocol/cloudburst")
	}
}
