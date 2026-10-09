plugins {
	id("module-java")
	// Worker contract tests start real workers on each release's locked closure instead of downloading it.
	id("test-library-closures")
}

description = "MCProtocolLib library provider, release data, worker host and the MCProtocolLib-free worker shell"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)
	compileOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability)
	testImplementation(projects.anvilCapability.capabilityBuiltin.default)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilProtocol.protocolApi)
	testImplementation(projects.anvilProtocol.protocolMcprotocol.mcprotocolApi)

	testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolClient)
	testRuntimeOnly(libs.slf4j.simple)
}

// The release data lives at the family root, where build-logic reads it for segments; the runtime reads this copy.
tasks.named<ProcessResources>("processResources") {
	from(layout.projectDirectory.file("../mcprotocol-releases.toml")) {
		into("me/whereareiam/anvil/protocol/mcprotocol")
	}
}
